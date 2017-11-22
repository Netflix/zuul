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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
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
    
    private static State getCurrentState(Channel ch) {
        return ch.attr(ATTR_STATE).get();
    }

    private static class InboundChannelHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            State state = null;
            
            // Reset the state as each new request comes in.
            if (msg instanceof HttpRequest) {
                state = new State();
                ctx.channel().attr(ATTR_STATE).set(state);
            }
            
            // Update the request body size with this chunk.
            if (msg instanceof HttpContent) {
                if (state == null) {
                    state = getCurrentState(ctx.channel());
                }
                state.requestBodySize += ((HttpContent) msg).content().readableBytes();
            }

            super.channelRead(ctx, msg);
        }
    }

    private static class OutboundChannelHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            State state = getCurrentState(ctx.channel());

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
            State state = getCurrentState(channel);
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
            State state = getCurrentState(channel);
            return state == null ? 0 : state.responseBodySize;
        }
    }
}
