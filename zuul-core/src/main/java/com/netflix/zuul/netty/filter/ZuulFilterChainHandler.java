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

package com.netflix.zuul.netty.filter;

import com.google.common.base.Preconditions;
import com.netflix.netty.common.HttpLifecycleChannelHandler;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpRequestReadTimeoutEvent;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.RequestCancelledEvent;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.unix.Errors;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import java.nio.channels.ClosedChannelException;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by saroskar on 5/18/17.
 */
public class ZuulFilterChainHandler extends ChannelInboundHandlerAdapter {

    private final ZuulFilterChainRunner<HttpRequestMessage> requestFilterChain;
    private final ZuulFilterChainRunner<HttpResponseMessage> responseFilterChain;
    private HttpRequestMessage zuulRequest;

    private static final Logger logger = LoggerFactory.getLogger(ZuulFilterChainHandler.class);

    public ZuulFilterChainHandler(
            ZuulFilterChainRunner<HttpRequestMessage> requestFilterChain,
            ZuulFilterChainRunner<HttpResponseMessage> responseFilterChain) {
        this.requestFilterChain = Preconditions.checkNotNull(requestFilterChain, "request filter chain");
        this.responseFilterChain = Preconditions.checkNotNull(responseFilterChain, "response filter chain");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequestMessage) {
            zuulRequest = (HttpRequestMessage) msg;

            // Replace NETTY_SERVER_CHANNEL_HANDLER_CONTEXT in SessionContext
            SessionContext zuulCtx = zuulRequest.getContext();
            zuulCtx.put(CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT, ctx);

            requestFilterChain.filter(zuulRequest);
        } else if ((msg instanceof HttpContent) && (zuulRequest != null)) {
            requestFilterChain.filter(zuulRequest, (HttpContent) msg);
        } else {
            logger.debug(
                    "Received unrecognized message type. {}", msg.getClass().getName());
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof CompleteEvent completeEvent) {
            
            fireEndpointFinish(
                    completeEvent.getReason() != HttpLifecycleChannelHandler.CompleteReason.SESSION_COMPLETE, ctx);
        } else if (evt instanceof HttpRequestReadTimeoutEvent) {
            sendResponse(ZuulStatusCategory.FAILURE_CLIENT_TIMEOUT, 408, ctx);
        } else if (evt instanceof IdleStateEvent) {
            sendResponse(ZuulStatusCategory.FAILURE_LOCAL_IDLE_TIMEOUT, 504, ctx);
        } else if (evt instanceof RequestCancelledEvent) {
            if (zuulRequest != null) {
                zuulRequest.getContext().cancel();
                StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(
                        zuulRequest.getContext(), ZuulStatusCategory.FAILURE_CLIENT_CANCELLED);
            }
            fireEndpointFinish(true, ctx);
            ctx.close();
        }
        super.userEventTriggered(ctx, evt);
    }

    private void sendResponse(StatusCategory statusCategory, int status, ChannelHandlerContext ctx) {
        if (zuulRequest == null) {
            ctx.close();
        } else {
            SessionContext zuulCtx = zuulRequest.getContext();
            zuulRequest.getContext().cancel();
            StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(zuulCtx, statusCategory);
            HttpResponseMessage zuulResponse = new HttpResponseMessageImpl(zuulCtx, zuulRequest, status);
            Headers headers = zuulResponse.getHeaders();
            headers.add("Connection", "close");
            headers.add("Content-Length", "0");
            zuulResponse.finishBufferedBodyIfIncomplete();
            responseFilterChain.filter(zuulResponse);
            fireEndpointFinish(true, ctx);
        }
    }

    protected HttpRequestMessage getZuulRequest() {
        return zuulRequest;
    }

    protected void fireEndpointFinish(boolean error, ChannelHandlerContext ctx) {
        // make sure filter chain is not left hanging
        finishResponseFilters(ctx);

        ZuulFilter endpoint = ZuulEndPointRunner.getEndpoint(zuulRequest);
        if (endpoint instanceof ProxyEndpoint edgeProxyEndpoint) {
            
            edgeProxyEndpoint.finish(error);
        }
        zuulRequest = null;
    }

    private void finishResponseFilters(ChannelHandlerContext ctx) {
        // check if there are any response filters awaiting a buffered body
        if (zuulRequest != null && responseFilterChain.isFilterAwaitingBody(zuulRequest.getContext())) {
            HttpResponseMessage zuulResponse =
                    ctx.channel().attr(ClientRequestReceiver.ATTR_ZUUL_RESP).get();
            if (zuulResponse != null) {
                // fire a last content into the filter chain to unblock any filters awaiting a buffered body
                responseFilterChain.filter(zuulResponse, new DefaultLastHttpContent());
                SpectatorUtils.newCounter(
                                "zuul.filterChain.bodyBuffer.hanging",
                                zuulRequest.getContext().getRouteVIP())
                        .increment();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof SSLException) {
            logger.debug("SSL exception not handled in filter chain", cause);
        } else {
            logger.error(
                    "zuul filter chain handler caught exception on channel: {}",
                    ChannelUtils.channelInfoForLogging(ctx.channel()),
                    cause);
        }
        if (zuulRequest != null && !isClientChannelClosed(cause)) {
            SessionContext zuulCtx = zuulRequest.getContext();
            zuulCtx.setError(cause);
            zuulCtx.setShouldSendErrorResponse(true);
            sendResponse(ZuulStatusCategory.FAILURE_LOCAL, 500, ctx);
        } else {
            fireEndpointFinish(true, ctx);
            ctx.close();
        }
    }

    // Race condition: channel.isActive() did not catch
    // channel close..resulting in an i/o exception
    private boolean isClientChannelClosed(Throwable cause) {
        if (cause instanceof ClosedChannelException || cause instanceof Errors.NativeIoException) {
            logger.error("ZuulFilterChainHandler::isClientChannelClosed - IO Exception");
            return true;
        }
        return false;
    }
}
