/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.netty.server;

import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by saroskar on 1/18/17.
 */
public class OriginResponseReceiver extends ChannelDuplexHandler {

    private volatile ProxyEndpoint edgeProxy;

    private static final Logger logger = LoggerFactory.getLogger(OriginResponseReceiver.class);
    private static final AttributeKey<Throwable> SSL_HANDSHAKE_UNSUCCESS_FROM_ORIGIN_THROWABLE =
            AttributeKey.newInstance("_ssl_handshake_from_origin_throwable");
    private static final AttributeKey<Boolean> SSL_CLOSE_NOTIFY_SEEN =
            AttributeKey.newInstance("_ssl_close_notify_seen");
    public static final String CHANNEL_HANDLER_NAME = "_origin_response_receiver";

    public OriginResponseReceiver(ProxyEndpoint edgeProxy) {
        this.edgeProxy = edgeProxy;
    }

    public void unlinkFromClientRequest() {
        edgeProxy = null;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try (TaskCloseable a = PerfMark.traceTask("ORR.channelRead")) {
            channelReadInternal(ctx, msg);
        }
    }

    protected void channelReadInternal(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            if (edgeProxy != null) {
                edgeProxy.responseFromOrigin((HttpResponse) msg);
            } else if (ReferenceCountUtil.refCnt(msg) > 0) {
                // this handles the case of a DefaultFullHttpResponse that could have content that needs to be released
                ReferenceCountUtil.safeRelease(msg);
            }
            ctx.channel().read();
        } else if (msg instanceof HttpContent chunk) {
            if (edgeProxy != null) {
                edgeProxy.invokeNext(chunk);
            } else {
                ReferenceCountUtil.safeRelease(chunk);
            }
            ctx.channel().read();
        } else {
            // should never happen
            ReferenceCountUtil.release(msg);
            Exception error = new IllegalStateException("Received invalid message from origin");
            if (edgeProxy != null) {
                edgeProxy.errorFromOrigin(error);
            }
            ctx.fireExceptionCaught(error);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof CompleteEvent completeEvent) {
            CompleteReason reason = completeEvent.getReason();
            if ((reason != CompleteReason.SESSION_COMPLETE) && (edgeProxy != null)) {
                if (reason == CompleteReason.CLOSE
                        && Objects.equals(ctx.channel().attr(SSL_CLOSE_NOTIFY_SEEN).get(), Boolean.TRUE)) {
                    logger.warn(
                            "Origin request completed with close, after getting a SslCloseCompletionEvent event: {}",
                            ChannelUtils.channelInfoForLogging(ctx.channel()));
                    edgeProxy.errorFromOrigin(new OriginConnectException(
                            "Origin connection close_notify", OutboundErrorType.CLOSE_NOTIFY_CONNECTION));
                } else {
                    logger.error(
                            "Origin request completed with reason other than COMPLETE: {}, {}",
                            reason.name(),
                            ChannelUtils.channelInfoForLogging(ctx.channel()));
                    ZuulException ze = new ZuulException("CompleteEvent", reason.name(), true);
                    edgeProxy.errorFromOrigin(ze);
                }
            }

            // First let this event propagate along the pipeline, before cleaning vars from the channel.
            // See channelWrite() where these vars are first set onto the channel.
            try {
                super.userEventTriggered(ctx, evt);
            } finally {
                postCompleteHook(ctx, evt);
            }
        } else if (evt instanceof SslHandshakeCompletionEvent && !((SslHandshakeCompletionEvent) evt).isSuccess()) {
            Throwable cause = ((SslHandshakeCompletionEvent) evt).cause();
            ctx.channel().attr(SSL_HANDSHAKE_UNSUCCESS_FROM_ORIGIN_THROWABLE).set(cause);
        } else if (evt instanceof IdleStateEvent) {
            if (edgeProxy != null) {
                logger.error(
                        "Origin request received IDLE event: {}", ChannelUtils.channelInfoForLogging(ctx.channel()));
                edgeProxy.errorFromOrigin(
                        new OutboundException(OutboundErrorType.READ_TIMEOUT, edgeProxy.getRequestAttempts()));
            }
            super.userEventTriggered(ctx, evt);
        } else if (evt instanceof SslCloseCompletionEvent) {
            logger.debug("Received SslCloseCompletionEvent on {}", ChannelUtils.channelInfoForLogging(ctx.channel()));
            ctx.channel().attr(SSL_CLOSE_NOTIFY_SEEN).set(true);
            super.userEventTriggered(ctx, evt);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * Override to add custom post complete functionality
     *
     * @param ctx - channel handler context
     * @param evt - netty event
     * @throws Exception
     */
    protected void postCompleteHook(ChannelHandlerContext ctx, Object evt) throws Exception {}

    private HttpRequest buildOriginHttpRequest(HttpRequestMessage zuulRequest) {
        String method = zuulRequest.getMethod().toUpperCase(Locale.ROOT);
        String uri = pathAndQueryString(zuulRequest);

        customRequestProcessing(zuulRequest);

        DefaultHttpRequest nettyReq =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri, false);
        // Copy headers across.
        for (Header h : zuulRequest.getHeaders().entries()) {
            nettyReq.headers().add(h.getKey(), h.getValue());
        }

        return nettyReq;
    }

    /**
     * Override to add custom modifications to the request before it goes out
     *
     * @param headers
     */
    protected void customRequestProcessing(HttpRequestMessage headers) {}

    private static String pathAndQueryString(HttpRequestMessage request) {
        // parsing the params cleans up any empty/null params using the logic of the HttpQueryParams class
        HttpQueryParams cleanParams =
                HttpQueryParams.parse(request.getQueryParams().toEncodedString());
        String cleanQueryStr = cleanParams.toEncodedString();
        if (cleanQueryStr == null || cleanQueryStr.isEmpty()) {
            return request.getPath();
        } else {
            return request.getPath() + "?" + cleanParams.toEncodedString();
        }
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try (TaskCloseable ignore = PerfMark.traceTask("ORR.writeInternal")) {
            writeInternal(ctx, msg, promise);
        }
    }

    private void writeInternal(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!ctx.channel().isActive()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (msg instanceof HttpRequestMessage) {
            promise.addListener((future) -> {
                if (!future.isSuccess()) {
                    Throwable cause = ctx.channel()
                            .attr(SSL_HANDSHAKE_UNSUCCESS_FROM_ORIGIN_THROWABLE)
                            .get();
                    if (cause != null) {
                        // Set the specific SSL handshake error if the handlers have already caught them
                        ctx.channel()
                                .attr(SSL_HANDSHAKE_UNSUCCESS_FROM_ORIGIN_THROWABLE)
                                .set(null);
                        fireWriteError("request headers", cause, ctx);
                        logger.debug(
                                "SSLException is overridden by SSLHandshakeException caught in handler level. Original"
                                        + " SSL exception message: ",
                                future.cause());
                    } else {
                        fireWriteError("request headers", future.cause(), ctx);
                    }
                }
            });

            HttpRequestMessage zuulReq = (HttpRequestMessage) msg;
            preWriteHook(ctx, zuulReq);

            super.write(ctx, buildOriginHttpRequest(zuulReq), promise);
        } else if (msg instanceof HttpContent) {
            promise.addListener((future) -> {
                if (!future.isSuccess()) {
                    fireWriteError("request content chunk", future.cause(), ctx);
                }
            });
            super.write(ctx, msg, promise);
        } else {
            // should never happen
            ReferenceCountUtil.release(msg);
            throw new ZuulException("Received invalid message from client", true);
        }
    }

    /**
     * Override to add custom pre-write functionality
     *
     * @param ctx     channel handler context
     * @param zuulReq request message to modify
     */
    protected void preWriteHook(ChannelHandlerContext ctx, HttpRequestMessage zuulReq) {}

    private void fireWriteError(String requestPart, Throwable cause, ChannelHandlerContext ctx) {
        String errMesg = "Error while proxying " + requestPart + " to origin ";
        if (edgeProxy != null) {
            ProxyEndpoint ep = edgeProxy;
            edgeProxy = null;
            errMesg += ep.getOrigin().getName();
            ep.errorFromOrigin(cause);
        }
        ctx.fireExceptionCaught(new ZuulException(cause, errMesg, true));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (edgeProxy != null) {
            if (cause instanceof ReadTimeoutException) {
                edgeProxy.getPassport().add(PassportState.ORIGIN_CH_READ_TIMEOUT);
                logger.debug(
                        "read timeout on origin channel {} ", ChannelUtils.channelInfoForLogging(ctx.channel()), cause);
            } else if (cause instanceof IOException) {
                edgeProxy.getPassport().add(PassportState.ORIGIN_CH_IO_EX);
                logger.debug(
                        "I/O error on origin channel {} ", ChannelUtils.channelInfoForLogging(ctx.channel()), cause);
            } else {
                logger.error("Error from Origin connection:", cause);
            }
            edgeProxy.errorFromOrigin(cause);
        }
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (edgeProxy != null) {
            logger.debug("Origin channel inactive. channel-info={}", ChannelUtils.channelInfoForLogging(ctx.channel()));
            OriginConnectException ex =
                    new OriginConnectException("Origin server inactive", OutboundErrorType.RESET_CONNECTION);
            edgeProxy.errorFromOrigin(ex);
        }
        super.channelInactive(ctx);
        ctx.close();
    }
}
