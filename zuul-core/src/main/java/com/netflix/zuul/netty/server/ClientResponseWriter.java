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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason.INACTIVE;
import static com.netflix.zuul.netty.server.ClientRequestReceiver.ATTR_ZUUL_RESP;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason.SESSION_COMPLETE;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.StartEvent;

/**
 * Created by saroskar on 2/26/17.
 */
public class ClientResponseWriter extends ChannelInboundHandlerAdapter {

    private static final Registry NOOP_REGISTRY = new NoopRegistry();

    private final RequestCompleteHandler requestCompleteHandler;
    private final Counter responseBeforeReceivedLastContentCounter;

    //state
    private boolean isHandlingRequest;
    private boolean startedSendingResponseToClient;
    private boolean closeConnection;

    //data
    private HttpResponseMessage zuulResponse;

    private static final Logger LOG = LoggerFactory.getLogger(ClientResponseWriter.class);

    public ClientResponseWriter(RequestCompleteHandler requestCompleteHandler) {
        this(requestCompleteHandler, NOOP_REGISTRY);
    }

    public ClientResponseWriter(RequestCompleteHandler requestCompleteHandler, Registry registry) {
        this.requestCompleteHandler = requestCompleteHandler;
        this.responseBeforeReceivedLastContentCounter = registry.counter("server.http.requests.responseBeforeReceivedLastContent");
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        final Channel channel = ctx.channel();

        if (msg instanceof HttpResponseMessage) {
            final HttpResponseMessage resp = (HttpResponseMessage) msg;

            if (skipProcessing(resp)) {
                return;
            }

            if ((! isHandlingRequest) || (startedSendingResponseToClient)) {
                /* This can happen if we are already in the process of streaming response back to client OR NOT within active
                   request/response cycle and something like IDLE or Request Read timeout occurs. In that case we have no way
                   to recover other than closing the socket and cleaning up resources used by BOTH responses.
                 */
                resp.disposeBufferedBody();
                if (zuulResponse != null) zuulResponse.disposeBufferedBody();
                ctx.close(); //This will trigger CompleteEvent if one is needed
                return;
            }

            startedSendingResponseToClient = true;
            zuulResponse = resp;
            if ("close".equalsIgnoreCase(zuulResponse.getHeaders().getFirst("Connection"))) {
                closeConnection = true;
            }
            channel.attr(ATTR_ZUUL_RESP).set(zuulResponse);

            if (channel.isActive()) {

                // Track if this is happening.
                if (! ClientRequestReceiver.isLastContentReceivedForChannel(channel)) {

                    StatusCategory status = StatusCategoryUtils.getStatusCategory(ClientRequestReceiver.getRequestFromChannel(channel));
                    if (ZuulStatusCategory.FAILURE_CLIENT_TIMEOUT.equals(status)) {
                        // If the request timed-out while being read, then there won't have been any LastContent, but thats ok because the connection will have to be discarded anyway.
                    }
                    else {
                        responseBeforeReceivedLastContentCounter.increment();
                        LOG.warn("Writing response to client channel before have received the LastContent of request! "
                                + zuulResponse.getInboundRequest().getInfoForLogging() + ", "
                                + ChannelUtils.channelInfoForLogging(channel));
                    }
                }

                // Write out and flush the response to the client channel.
                channel.write(buildHttpResponse(zuulResponse));
                writeBufferedBodyContent(zuulResponse, channel);
                channel.flush();
            } else {
                channel.close();
            }
        }
        else if (msg instanceof HttpContent) {
            final HttpContent chunk = (HttpContent) msg;
            if (channel.isActive()) {
                channel.writeAndFlush(chunk);
            } else {
                chunk.release();
                channel.close();
            }
        }
        else {
            //should never happen
            ReferenceCountUtil.release(msg);
            throw new ZuulException("Received invalid message from origin", true);
        }
    }

    protected boolean skipProcessing(HttpResponseMessage resp) {
        // override if you need to skip processing of response
        return false;
    }

    private static void writeBufferedBodyContent(final HttpResponseMessage zuulResponse, final Channel channel) {
        zuulResponse.getBodyContents().forEach(chunk -> channel.write(chunk.retain()));
    }

    private HttpResponse buildHttpResponse(final HttpResponseMessage zuulResp) {
        final HttpRequestInfo zuulRequest = zuulResp.getInboundRequest();
        HttpVersion responseHttpVersion;
        final String inboundProtocol = zuulRequest.getProtocol();
        if (inboundProtocol.startsWith("HTTP/1")) {
            responseHttpVersion = HttpVersion.valueOf(inboundProtocol);
        }
        else {
            // Default to 1.1. We do this to cope with HTTP/2 inbound requests.
            responseHttpVersion = HttpVersion.HTTP_1_1;
        }

        // Create the main http response to send, with body.
        final DefaultHttpResponse nativeResponse = new DefaultHttpResponse(responseHttpVersion,
                HttpResponseStatus.valueOf(zuulResp.getStatus()), false, false);

        // Now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
        final HttpHeaders nativeHeaders = nativeResponse.headers();
        for (Header entry : zuulResp.getHeaders().entries()) {
            nativeHeaders.add(entry.getKey(), entry.getValue());
        }

        // Netty does not automatically add Content-Length or Transfer-Encoding: chunked. So we add here if missing.
        if (! HttpUtil.isContentLengthSet(nativeResponse) && ! HttpUtil.isTransferEncodingChunked(nativeResponse)) {
            nativeResponse.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }

        final HttpRequest nativeReq = (HttpRequest) zuulResp.getContext().get(CommonContextKeys.NETTY_HTTP_REQUEST);
        if (!closeConnection && HttpUtil.isKeepAlive(nativeReq)) {
            HttpUtil.setKeepAlive(nativeResponse, true);
        } else {
            // Send a Connection: close response header (only needed for HTTP/1.0 but no harm in doing for 1.1 too).
            nativeResponse.headers().set("Connection", "close");
        }

        // TODO - temp hack for http/2 handling.
        if (nativeReq.headers().contains(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text())) {
            String streamId = nativeReq.headers().get(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
            nativeResponse.headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
        }

        return nativeResponse;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof StartEvent) {
            isHandlingRequest = true;
            startedSendingResponseToClient = false;
            closeConnection = false;
            zuulResponse = null;
        }
        else if (evt instanceof CompleteEvent) {
            HttpResponse response = ((CompleteEvent) evt).getResponse();
            if (response != null) {
                if ("close".equalsIgnoreCase(response.headers().get("Connection"))) {
                    closeConnection = true;
                }
            }
            if (zuulResponse != null) {
                zuulResponse.disposeBufferedBody();
            }

            // Do all the post-completion metrics and logging.
            handleComplete(ctx.channel());

            // Choose to either close the connection, or prepare it for next use.
            final CompleteEvent completeEvent = (CompleteEvent)evt;
            final CompleteReason reason = completeEvent.getReason();
            if (reason == SESSION_COMPLETE || reason == INACTIVE) {
                if (! closeConnection) {
                    //Start reading next request over HTTP 1.1 persistent connection
                    ctx.channel().read();
                } else {
                    ctx.close();
                }
            }
            else {
                if (isHandlingRequest) {
                    LOG.debug("Received complete event while still handling the request. With reason: " + reason.name() + " -- " +
                            ChannelUtils.channelInfoForLogging(ctx.channel()));
                }
                ctx.close();
            }

            isHandlingRequest = false;
        }
        else if (evt instanceof IdleStateEvent) {
            LOG.debug("Received IdleStateEvent.");
        }
        else {
            LOG.debug("ClientResponseWriter Received event {}", evt);
        }
    }

    private void handleComplete(Channel channel) {
        try {
            if ((isHandlingRequest)) {
                completeMetrics(channel, zuulResponse);

                // Notify requestComplete listener if configured.
                final HttpRequestMessage zuulRequest = ClientRequestReceiver.getRequestFromChannel(channel);
                if ((requestCompleteHandler != null) && (zuulRequest != null)) {
                    requestCompleteHandler.handle(zuulRequest.getInboundRequest(), zuulResponse);
                }
            }
        }
        catch (Throwable ex) {
            LOG.error("Error in RequestCompleteHandler.", ex);
        }
    }

    protected void completeMetrics(Channel channel, HttpResponseMessage zuulResponse) {
        // override for recording complete metrics
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        int status = 500;
        final String errorMsg = "ClientResponseWriter caught exception in client connection pipeline: " +
                ChannelUtils.channelInfoForLogging(ctx.channel());

        if (cause instanceof ZuulException) {
            final ZuulException ze = (ZuulException) cause;
            status = ze.getStatusCode();
            LOG.error(errorMsg, cause);
        }
        else if (cause instanceof ReadTimeoutException) {
            LOG.error(errorMsg + ", Read timeout fired");
            status = 504;
        }
        else {
            LOG.error(errorMsg, cause);
        }

        if (isHandlingRequest && !startedSendingResponseToClient && ctx.channel().isActive()) {
            final HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(status));
            ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
            startedSendingResponseToClient = true;
        }
        else {
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ctx.close();
    }

}
