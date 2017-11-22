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

import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.netflix.zuul.exception.OutboundErrorType.READ_TIMEOUT;
import static com.netflix.zuul.exception.OutboundErrorType.RESET_CONNECTION;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason.SESSION_COMPLETE;

/**
 * Created by saroskar on 1/18/17.
 */
public class OriginResponseReceiver extends ChannelDuplexHandler {

    private volatile ProxyEndpoint edgeProxy;

    private static final Logger LOG = LoggerFactory.getLogger(OriginResponseReceiver.class);
    public static final String CHANNEL_HANDLER_NAME = "_origin_response_receiver";

    public OriginResponseReceiver(final ProxyEndpoint edgeProxy) {
        this.edgeProxy = edgeProxy;
    }

    public void unlinkFromClientRequest() {
        edgeProxy = null;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            if (edgeProxy != null) {
                edgeProxy.responseFromOrigin((HttpResponse) msg);
            }
            ctx.channel().read();
        }
        else if (msg instanceof HttpContent) {
            final HttpContent chunk = (HttpContent) msg;
            if (edgeProxy != null) {
                edgeProxy.invokeNext(chunk);
            }
            else {
                chunk.release();
            }
            ctx.channel().read();
        }
        else {
            //should never happen
            ReferenceCountUtil.release(msg);
            final Exception error = new IllegalStateException("Received invalid message from origin");
            if (edgeProxy != null) {
                edgeProxy.errorFromOrigin(error);
            }
            ctx.fireExceptionCaught(error);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof CompleteEvent) {
            final CompleteReason reason = ((CompleteEvent) evt).getReason();
            if ((reason != SESSION_COMPLETE) && (edgeProxy != null)) {
                LOG.error("Origin request completed with reason other than COMPLETE: {}, {}",
                        reason.name(), ChannelUtils.channelInfoForLogging(ctx.channel()));
                final ZuulException ze = new ZuulException("CompleteEvent", reason.name(), true);
                edgeProxy.errorFromOrigin(ze);
            }

            // First let this event propagate along the pipeline, before cleaning vars from the channel.
            // See channelWrite() where these vars are first set onto the channel.
            try {
                super.userEventTriggered(ctx, evt);
            }
            finally {
                postCompleteHook(ctx, evt);
            }
        }
        else if (evt instanceof IdleStateEvent) {
            if (edgeProxy != null) {
                LOG.error("Origin request received IDLE event: {}", ChannelUtils.channelInfoForLogging(ctx.channel()));
                edgeProxy.errorFromOrigin(new OutboundException(READ_TIMEOUT, edgeProxy.getRequestAttempts()));
            }
            super.userEventTriggered(ctx, evt);
        }
        else {
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
    protected void postCompleteHook(ChannelHandlerContext ctx, Object evt) throws Exception {
    }

    private HttpRequest buildOriginHttpRequest(final HttpRequestMessage zuulRequest) {
        final String method = zuulRequest.getMethod().toUpperCase();
        final String uri = pathAndQueryString(zuulRequest);

        customRequestProcessing(zuulRequest);

        final DefaultHttpRequest nettyReq = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri, false);
        // Copy headers across.
        for (final Header h : zuulRequest.getHeaders().entries()) {
            nettyReq.headers().add(h.getKey(), h.getValue());
        }

        return nettyReq;
    }

    /**
     * Override to add custom modifications to the request before it goes out
     *
     * @param headers
     */
    protected void customRequestProcessing(HttpRequestMessage headers) {
    }

    private static String pathAndQueryString(HttpRequestMessage request) {
        // parsing the params cleans up any empty/null params using the logic of the HttpQueryParams class
        final HttpQueryParams cleanParams = HttpQueryParams.parse(request.getQueryParams().toEncodedString());
        final String cleanQueryStr = cleanParams.toEncodedString();
        if (cleanQueryStr == null || cleanQueryStr.isEmpty()) {
            return request.getPath();
        }
        else {
            return request.getPath() + "?" + cleanParams.toEncodedString();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!ctx.channel().isActive()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (msg instanceof HttpRequestMessage) {
            promise.addListener((future) -> {
                if (!future.isSuccess()) {
                    fireWriteError("request headers", future.cause(), ctx);
                }
            });

            HttpRequestMessage zuulReq = (HttpRequestMessage) msg;
            preWriteHook(ctx, zuulReq);

            super.write(ctx, buildOriginHttpRequest(zuulReq), promise);
        }
        else if (msg instanceof HttpContent) {
            promise.addListener((future) -> {
                if (!future.isSuccess()) {
                    fireWriteError("request content chunk", future.cause(), ctx);
                }
            });
            super.write(ctx, msg, promise);
        }
        else {
            //should never happen
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
    protected void preWriteHook(ChannelHandlerContext ctx, HttpRequestMessage zuulReq) {
    }

    private void fireWriteError(String requestPart, Throwable cause, ChannelHandlerContext ctx) throws Exception {
        String errMesg = "Error while proxying " + requestPart + " to origin ";
        if (edgeProxy != null) {
            final ProxyEndpoint ep = edgeProxy;
            edgeProxy = null;
            errMesg += ep.getOrigin().getName();
            ep.errorFromOrigin(cause);
        }
        ctx.fireExceptionCaught(new ZuulException(cause, errMesg, true));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (edgeProxy != null) {
            LOG.error("Error from Origin connection", cause);
            if (cause instanceof ReadTimeoutException) {
                edgeProxy.getPassport().add(PassportState.ORIGIN_CH_READ_TIMEOUT);
            }
            else if (cause instanceof IOException) {
                edgeProxy.getPassport().add(PassportState.ORIGIN_CH_IO_EX);
            }
            edgeProxy.errorFromOrigin(cause);
        }
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (edgeProxy != null) {
            LOG.debug("Origin channel inactive. channel-info={}", ChannelUtils.channelInfoForLogging(ctx.channel()));
            OriginConnectException ex = new OriginConnectException("Origin server inactive", RESET_CONNECTION);
            edgeProxy.errorFromOrigin(ex);
        }
        super.channelInactive(ctx);
    }

}
