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

import com.netflix.netty.common.throttle.MaxInboundConnectionsHandler;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: Mike Smith
 * Date: 3/5/16
 * Time: 5:47 PM
 */
@ChannelHandler.Sharable
public class ServerChannelMetrics extends ChannelInboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(ServerChannelMetrics.class);
    private static final AttributeKey<AtomicInteger> ATTR_CURRENT_CONNS = AttributeKey.newInstance("_server_connections_count");

    private final Gauge currentConnectionsGauge;
    private final AtomicInteger currentConnections = new AtomicInteger(0);
    private final Counter totalConnections;
    private final Counter connectionClosed;
    private final Counter connectionErrors;
    private final Counter connectionThrottled;

    public ServerChannelMetrics(String id, Registry registry) {
        String metricNamePrefix = "server.connections.";
        currentConnectionsGauge = registry.gauge(metricNamePrefix + "current", "id", id);
        totalConnections = registry.counter(metricNamePrefix + "connect", "id", id);
        connectionErrors = registry.counter(metricNamePrefix + "errors", "id", id);
        connectionClosed = registry.counter(metricNamePrefix + "close", "id", id);
        connectionThrottled = registry.counter(metricNamePrefix + "throttled", "id", id);
    }

    public static int currentConnectionCountFromChannel(Channel ch)
    {
        AtomicInteger count = ch.attr(ATTR_CURRENT_CONNS).get();
        return count == null ? 0 : count.get();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        currentConnectionsGauge.set(currentConnections.incrementAndGet());
        totalConnections.increment();
        ctx.channel().attr(ATTR_CURRENT_CONNS).set(currentConnections);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        try {
            super.channelInactive(ctx);
        }
        finally {
            currentConnectionsGauge.set(currentConnections.decrementAndGet());
            connectionClosed.increment();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        connectionErrors.increment();
        if (LOG.isInfoEnabled()) {
            LOG.info("Connection error caught. " + String.valueOf(cause), cause);
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        if (evt == MaxInboundConnectionsHandler.CONNECTION_THROTTLED_EVENT) {
            connectionThrottled.increment();
        }

        super.userEventTriggered(ctx, evt);
    }
}
