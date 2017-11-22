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

package com.netflix.netty.common;

import com.netflix.servo.monitor.BasicCounter;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.concurrent.TimeUnit;

/**
 * This handler times from the point a HttpRequest is read until the LastHttpContent is read,
 * and fires a HttpRequestTimeoutEvent if that time has exceed the configured timeout.
 *
 * Unlike ReadTimeoutHandler, this impl does NOT close the channel on a timeout. Only fires the
 * event.
 *
 * @author michaels
 */
public class HttpRequestReadTimeoutHandler extends ChannelInboundHandlerAdapter
{
    private static final String HANDLER_NAME = "http_request_read_timeout_handler";
    private static final String INTERNAL_HANDLER_NAME = "http_request_read_timeout_internal";

    private final long timeout;
    private final TimeUnit unit;
    private final BasicCounter httpRequestReadTimeoutCounter;

    protected HttpRequestReadTimeoutHandler(long timeout, TimeUnit unit, BasicCounter httpRequestReadTimeoutCounter)
    {
        this.timeout = timeout;
        this.unit = unit;
        this.httpRequestReadTimeoutCounter = httpRequestReadTimeoutCounter;
    }

    /**
     * Factory which ensures that this handler is added to the pipeline using the
     * correct name.
     *
     * @param timeout
     * @param unit
     */
    public static void addLast(ChannelPipeline pipeline, long timeout, TimeUnit unit, BasicCounter httpRequestReadTimeoutCounter)
    {
        HttpRequestReadTimeoutHandler handler = new HttpRequestReadTimeoutHandler(timeout, unit, httpRequestReadTimeoutCounter);
        pipeline.addLast(HANDLER_NAME, handler);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof LastHttpContent) {
            removeInternalHandler(ctx);
        }
        else if (msg instanceof HttpRequest) {
            // Start timeout handler.
            InternalReadTimeoutHandler handler = new InternalReadTimeoutHandler(timeout, unit);
            ctx.pipeline().addBefore(HANDLER_NAME, INTERNAL_HANDLER_NAME, handler);
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        if (evt instanceof HttpRequestReadTimeoutEvent) {
            CurrentPassport.fromChannel(ctx.channel()).add(PassportState.IN_REQ_READ_TIMEOUT);
            removeInternalHandler(ctx);
            httpRequestReadTimeoutCounter.increment();
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception
    {
        removeInternalHandler(ctx);
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        removeInternalHandler(ctx);
        super.channelInactive(ctx);
    }

    protected void removeInternalHandler(ChannelHandlerContext ctx)
    {
        // Remove timeout handler if not already removed.
        ChannelHandlerContext handlerContext = ctx.pipeline().context(INTERNAL_HANDLER_NAME);
        if (handlerContext != null && ! handlerContext.isRemoved()) {
            ctx.pipeline().remove(INTERNAL_HANDLER_NAME);
        }
    }


    static class InternalReadTimeoutHandler extends ReadTimeoutHandler
    {
        public InternalReadTimeoutHandler(long timeout, TimeUnit unit)
        {
            super(timeout, unit);
        }

        @Override
        protected void readTimedOut(ChannelHandlerContext ctx) throws Exception
        {
            ctx.fireUserEventTriggered(HttpRequestReadTimeoutEvent.INSTANCE);
        }
    }
}
