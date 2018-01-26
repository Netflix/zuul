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

package com.netflix.netty.common.throttle;

import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Closes any incoming new connections if current count is above a configured threshold.
 *
 * When a connection is throttled, the channel is closed, and then a CONNECTION_THROTTLED_EVENT event is fired
 * not notify any other interested handlers.
 *
 */
@ChannelHandler.Sharable
public class MaxInboundConnectionsHandler extends ChannelInboundHandlerAdapter
{
    public static final String CONNECTION_THROTTLED_EVENT = "connection_throttled";

    private static final Logger LOG = LoggerFactory.getLogger(MaxInboundConnectionsHandler.class);
    private static final AttributeKey<Boolean> ATTR_CH_THROTTLED = AttributeKey.newInstance("_channel_throttled");

    private final static AtomicInteger connections = new AtomicInteger(0);
    private final int maxConnections;

    public MaxInboundConnectionsHandler(int maxConnections)
    {
        this.maxConnections = maxConnections;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        if (maxConnections > 0) {
            int currentCount = connections.getAndIncrement();

            if (currentCount + 1 > maxConnections) {
                LOG.warn("Throttling incoming connection as above configured max connections threshold of " + maxConnections);
                Channel channel = ctx.channel();
                channel.attr(ATTR_CH_THROTTLED).set(Boolean.TRUE);
                CurrentPassport.fromChannel(channel).add(PassportState.SERVER_CH_THROTTLING);
                channel.close();
                ctx.pipeline().fireUserEventTriggered(CONNECTION_THROTTLED_EVENT);
            }
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (ctx.channel().attr(ATTR_CH_THROTTLED).get() != null) {
            // Discard this msg as channel is in process of being closed.
        }
        else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        if (maxConnections > 0) {
            connections.decrementAndGet();
        }

        super.channelInactive(ctx);
    }
}
