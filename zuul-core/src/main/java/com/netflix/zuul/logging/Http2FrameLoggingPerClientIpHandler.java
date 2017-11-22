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

package com.netflix.zuul.logging;

import com.netflix.config.DynamicStringSetProperty;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.http2.DynamicHttp2FrameLogger;

/**
 * Be aware that this will only work correctly for devices connected _directly_ to Zuul - ie. connected
 * through an ELB TCP Listener. And not through FTL either.
 */
public class Http2FrameLoggingPerClientIpHandler extends ChannelInboundHandlerAdapter
{
    private static DynamicStringSetProperty IPS = 
            new DynamicStringSetProperty("server.http2.frame.logging.ips", "");
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        try {
            String clientIP = ctx.channel().attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get();

            if (IPS.get().contains(clientIP)) {
                ctx.channel().attr(DynamicHttp2FrameLogger.ATTR_ENABLE).set(Boolean.TRUE);
                ctx.pipeline().remove(this);
            }
        }
        finally {
            super.channelRead(ctx, msg);
        }
    }
}
