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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * @author michaels
 */
public class HttpClientLifecycleChannelHandler extends HttpLifecycleChannelHandler
{
    private static final HttpClientLifecycleInboundChannelHandler INBOUND_CHANNEL_HANDLER = new HttpClientLifecycleInboundChannelHandler();
    private static final HttpClientLifecycleOutboundChannelHandler OUTBOUND_CHANNEL_HANDLER = new HttpClientLifecycleOutboundChannelHandler();

    public HttpClientLifecycleChannelHandler()
    {
        super(INBOUND_CHANNEL_HANDLER, OUTBOUND_CHANNEL_HANDLER);
    }


    @Sharable
    private static class HttpClientLifecycleInboundChannelHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            if (msg instanceof HttpResponse) {
                ctx.channel().attr(ATTR_HTTP_RESP).set((HttpResponse) msg);
            }

            try {
                super.channelRead(ctx, msg);
            }
            finally {
                if (msg instanceof LastHttpContent) {
                    fireCompleteEventIfNotAlready(ctx, CompleteReason.SESSION_COMPLETE);
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception
        {
            try {
                super.channelInactive(ctx);
            }
            finally {
                fireCompleteEventIfNotAlready(ctx, CompleteReason.INACTIVE);
            }
        }

    }

    @Sharable
    private static class HttpClientLifecycleOutboundChannelHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            if (msg instanceof HttpRequest) {
                fireStartEvent(ctx, (HttpRequest) msg);
            }

            super.write(ctx, msg, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            fireCompleteEventIfNotAlready(ctx, CompleteReason.DISCONNECT);

            super.disconnect(ctx, promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            fireCompleteEventIfNotAlready(ctx, CompleteReason.DEREGISTER);

            super.deregister(ctx, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            super.exceptionCaught(ctx, cause);

            fireCompleteEventIfNotAlready(ctx, CompleteReason.EXCEPTION);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            fireCompleteEventIfNotAlready(ctx, CompleteReason.CLOSE);

            super.close(ctx, promise);
        }
    }
}
