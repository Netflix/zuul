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

import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * @author michaels
 */
public class HttpServerLifecycleChannelHandler extends HttpLifecycleChannelHandler
{
    public HttpServerLifecycleChannelHandler()
    {
        super(new HttpServerLifecycleInboundChannelHandler(), new HttpServerLifecycleOutboundChannelHandler());
    }

    private static class HttpServerLifecycleInboundChannelHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            if (msg instanceof HttpRequest) {
                // Fire start event, and if that succeeded, then allow processing to 
                // continue to next handler in pipeline.
                if (fireStartEvent(ctx, (HttpRequest) msg)) {
                    super.channelRead(ctx, msg);
                }
            }
            else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception
        {
            fireCompleteEventIfNotAlready(ctx, CompleteReason.INACTIVE);

            super.channelInactive(ctx);
        }

    }

    private static class HttpServerLifecycleOutboundChannelHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            if (msg instanceof HttpResponse) {
                ctx.channel().attr(ATTR_HTTP_RESP).set((HttpResponse) msg);
            }
            
            try {
                super.write(ctx, msg, promise);
            }
            finally {
                if (msg instanceof LastHttpContent) {

                    boolean dontFireCompleteYet = false;
                    if (msg instanceof HttpResponse) {
                        // Handle case of 100 CONTINUE, where server sends an initial 100 status response to indicate to client
                        // that it can continue sending the initial request body.
                        // ie. in this case we don't want to consider the state to be COMPLETE until after the 2nd response.
                        if (((HttpResponse) msg).status() == HttpResponseStatus.CONTINUE) {
                            dontFireCompleteYet = true;
                        }
                    }

                    if (!dontFireCompleteYet)
                        if (promise.isDone()) {
                            fireCompleteEventIfNotAlready(ctx, CompleteReason.SESSION_COMPLETE);
                        } else {
                            promise.addListener(future -> {
                                fireCompleteEventIfNotAlready(ctx, CompleteReason.SESSION_COMPLETE);
                            });
                        }
                }
            }
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            fireCompleteEventIfNotAlready(ctx, CompleteReason.DISCONNECT);

            super.disconnect(ctx, promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            addPassportState(ctx, PassportState.SERVER_CH_CLOSE);
            
            fireCompleteEventIfNotAlready(ctx, CompleteReason.CLOSE);

            super.close(ctx, promise);
        }

    }
}
