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

package com.netflix.netty.common.metrics;

import com.netflix.netty.common.HttpLifecycleChannelHandler;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;

import javax.inject.Provider;

/**
 * User: michaels@netflix.com
 * Date: 4/14/16
 * Time: 3:51 PM
 */
public class HttpBodySizeRecordingChannelHandler extends CombinedChannelDuplexHandler
{
    private static final AttributeKey<State> ATTR_STATE = AttributeKey.newInstance("_http_body_size_state");

    public HttpBodySizeRecordingChannelHandler()
    {
        super(new InboundChannelHandler(), new OutboundChannelHandler());
    }
    
    public static Provider<Long> getCurrentRequestBodySize(Channel ch)
    {
        return new RequestBodySizeProvider(ch);
    }

    public static Provider<Long> getCurrentResponseBodySize(Channel ch)
    {
        return new ResponseBodySizeProvider(ch);
    }
    
    private static State getOrCreateCurrentState(Channel ch)
    {
        State state = ch.attr(ATTR_STATE).get();
        if (state == null) {
            state = createNewState(ch);
        }
        return state;
    }

    private static State createNewState(Channel ch)
    {
        State state = new State();
        ch.attr(ATTR_STATE).set(state);
        return state;
    }

    private static class InboundChannelHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            State state = null;
            
            // Reset the state as each new request comes in.
            if (msg instanceof HttpRequest) {
                state = createNewState(ctx.channel());
            }
            
            // Update the request body size with this chunk.
            if (msg instanceof HttpContent) {
                if (state == null) {
                    state = getOrCreateCurrentState(ctx.channel());
                }
                state.requestBodySize += ((HttpContent) msg).content().readableBytes();
            }

            super.channelRead(ctx, msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
        {
            try {
                super.userEventTriggered(ctx, evt);
            }
            finally {
                if (evt instanceof HttpLifecycleChannelHandler.CompleteEvent) {
                    ctx.channel().attr(ATTR_STATE).set(null);
                }
            }
        }
    }

    private static class OutboundChannelHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            State state = getOrCreateCurrentState(ctx.channel());

            // Update the response body size with this chunk.
            if (msg instanceof HttpContent) {
                state.responseBodySize += ((HttpContent) msg).content().readableBytes();
            }

            super.write(ctx, msg, promise);
        }
    }

    private static class State
    {
        long requestBodySize = 0;
        long responseBodySize = 0;
    }
    
    static class RequestBodySizeProvider implements Provider<Long>
    {
        private final Channel channel;

        public RequestBodySizeProvider(Channel channel)
        {
            this.channel = channel;
        }

        @Override
        public Long get()
        {
            State state = getOrCreateCurrentState(channel);
            return state == null ? 0 : state.requestBodySize;
        }
    }

    static class ResponseBodySizeProvider implements Provider<Long>
    {
        private final Channel channel;

        public ResponseBodySizeProvider(Channel channel)
        {
            this.channel = channel;
        }

        @Override
        public Long get()
        {
            State state = getOrCreateCurrentState(channel);
            return state == null ? 0 : state.responseBodySize;
        }
    }
}
