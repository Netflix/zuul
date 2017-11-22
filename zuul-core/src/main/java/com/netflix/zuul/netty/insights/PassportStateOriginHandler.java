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

import java.net.SocketAddress;

/**
 * User: Mike Smith
 * Date: 9/24/16
 * Time: 2:41 PM
 */
public class PassportStateOriginHandler extends CombinedChannelDuplexHandler
{
    public PassportStateOriginHandler()
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
            passport(ctx).add(PassportState.ORIGIN_CH_ACTIVE);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception
        {
            passport(ctx).add(PassportState.ORIGIN_CH_INACTIVE);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            passport(ctx).add(PassportState.ORIGIN_CH_EXCEPTION);
            super.exceptionCaught(ctx, cause);
        }
    }

    private static class OutboundHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            passport(ctx).add(PassportState.ORIGIN_CH_DISCONNECT);
            super.disconnect(ctx, promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            passport(ctx).add(PassportState.ORIGIN_CH_CLOSE);
            super.close(ctx, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            passport(ctx).add(PassportState.ORIGIN_CH_EXCEPTION);
            super.exceptionCaught(ctx, cause);
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception
        {
            // We would prefer to set this passport state here, but if we do then it will be run _after_ the http request
            // has actually been written to the channel. Because another listener is added before this one.
            // So instead we have to add this listener in the PerServerConnectionPool.handleConnectCompletion() method instead.
            //passport.add(PassportState.ORIGIN_CH_CONNECTING);
            //promise.addListener(new PassportStateListener(passport, PassportState.ORIGIN_CH_CONNECTED));
            
            super.connect(ctx, remoteAddress, localAddress, promise);
        }
    }
}
