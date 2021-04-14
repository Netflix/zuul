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
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.netty.RequestCancelledEvent;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.unix.Errors;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import com.netflix.netty.common.HttpLifecycleChannelHandler;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpRequestReadTimeoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;

import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason.SESSION_COMPLETE;
import static com.netflix.zuul.context.CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT;
import static com.netflix.zuul.context.CommonContextKeys.ZUUL_FILTER_CHAIN;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_CLIENT_CANCELLED;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_CLIENT_PIPELINE_REJECT;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_CLIENT_TIMEOUT;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_LOCAL;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_LOCAL_IDLE_TIMEOUT;

/**
 * Created by saroskar on 5/18/17.
 */
public class ZuulFilterChainHandler extends ChannelInboundHandlerAdapter {

    private final ZuulFilterChainRunner<HttpRequestMessage> requestFilterChain;
    private final ZuulFilterChainRunner<HttpResponseMessage> responseFilterChain;
    private HttpRequestMessage zuulRequest;

    private static final Logger LOG = LoggerFactory.getLogger(ZuulFilterChainHandler.class);


    public ZuulFilterChainHandler(ZuulFilterChainRunner<HttpRequestMessage> requestFilterChain,
                                  ZuulFilterChainRunner<HttpResponseMessage> responseFilterChain) {
        this.requestFilterChain = Preconditions.checkNotNull(requestFilterChain, "request filter chain");
        this.responseFilterChain = Preconditions.checkNotNull(responseFilterChain, "response filter chain");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequestMessage) {
            zuulRequest = (HttpRequestMessage)msg;

            //Replace NETTY_SERVER_CHANNEL_HANDLER_CONTEXT in SessionContext
            final SessionContext zuulCtx = zuulRequest.getContext();
            zuulCtx.put(NETTY_SERVER_CHANNEL_HANDLER_CONTEXT, ctx);
            zuulCtx.put(ZUUL_FILTER_CHAIN, requestFilterChain);

            requestFilterChain.filter(zuulRequest);
        }
        else if ((msg instanceof HttpContent)&&(zuulRequest != null)) {
            requestFilterChain.filter(zuulRequest, (HttpContent) msg);
        }
        else {
            LOG.debug("Received unrecognized message type. " + msg.getClass().getName());
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof CompleteEvent) {
            final CompleteEvent completeEvent = (CompleteEvent)evt;
            fireEndpointFinish(completeEvent.getReason() != SESSION_COMPLETE);
        }
        else if (evt instanceof HttpRequestReadTimeoutEvent) {
            sendResponse(FAILURE_CLIENT_TIMEOUT, 408, ctx);
        }
        else if (evt instanceof IdleStateEvent) {
            sendResponse(FAILURE_LOCAL_IDLE_TIMEOUT, 504, ctx);
        }
        else if (evt instanceof RequestCancelledEvent) {
            if (zuulRequest != null) {
                StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(zuulRequest.getContext(), FAILURE_CLIENT_CANCELLED);
            }
            fireEndpointFinish(true);
            ctx.close();
        }
        super.userEventTriggered(ctx, evt);
    }

    private void sendResponse(final StatusCategory statusCategory, final int status, ChannelHandlerContext ctx) {
        if (zuulRequest == null) {
            ctx.close();
        }
        else {
            final SessionContext zuulCtx = zuulRequest.getContext();
            StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(zuulCtx, statusCategory);
            final HttpResponseMessage zuulResponse = new HttpResponseMessageImpl(zuulCtx, zuulRequest, status);
            final Headers headers = zuulResponse.getHeaders();
            headers.add("Connection", "close");
            headers.add("Content-Length", "0");
            zuulResponse.finishBufferedBodyIfIncomplete();
            responseFilterChain.filter(zuulResponse);
            fireEndpointFinish(true);
        }
    }

    protected HttpRequestMessage getZuulRequest() {
        return zuulRequest;
    }

    protected void fireEndpointFinish(final boolean error) {
        final ZuulFilter endpoint = ZuulEndPointRunner.getEndpoint(zuulRequest);
        if (endpoint instanceof ProxyEndpoint) {
            final ProxyEndpoint edgeProxyEndpoint = (ProxyEndpoint) endpoint;
            edgeProxyEndpoint.finish(error);
        }
        zuulRequest = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("zuul filter chain handler caught exception. cause=" + String.valueOf(cause), cause);
        if (zuulRequest != null && !isClientChannelClosed(cause)) {
            final SessionContext zuulCtx = zuulRequest.getContext();
            zuulCtx.setError(cause);
            zuulCtx.setShouldSendErrorResponse(true);
            sendResponse(FAILURE_LOCAL, 500, ctx);
        } else {
            fireEndpointFinish(true);
            ctx.close();
        }
    }


    // Race condition: channel.isActive() did not catch
    // channel close..resulting in an i/o exception
    private boolean isClientChannelClosed(Throwable cause) {
        if (cause instanceof ClosedChannelException ||
                cause instanceof Errors.NativeIoException) {
            LOG.error("ZuulFilterChainHandler::isClientChannelClosed - IO Exception");
            return true;
        }
        return false;
    }
}
