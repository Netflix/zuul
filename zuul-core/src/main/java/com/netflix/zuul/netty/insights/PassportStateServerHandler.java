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

package com.netflix.zuul.netty.insights;

import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * User: Mike Smith
 * Date: 9/24/16
 * Time: 2:41 PM
 */
public class PassportStateServerHandler extends CombinedChannelDuplexHandler
{
    public PassportStateServerHandler()
    {
        super(new InboundHandler(), new OutboundHandler());
    }

    private static CurrentPassport passport(ChannelHandlerContext ctx)
    {
        return CurrentPassport.fromChannel(ctx.channel());
    }
    
    private static class InboundHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_ACTIVE);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_INACTIVE);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_EXCEPTION);
            super.exceptionCaught(ctx, cause);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                final CurrentPassport passport = CurrentPassport.fromChannel(ctx.channel());
                if (passport != null) {
                    passport.add(PassportState.SERVER_CH_IDLE_TIMEOUT);
                }
            }

            super.userEventTriggered(ctx, evt);
        }
    }

    private static class OutboundHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_CLOSE);
            super.close(ctx, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_DISCONNECT);
            super.disconnect(ctx, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_EXCEPTION);
            super.exceptionCaught(ctx, cause);
        }
    }
}
