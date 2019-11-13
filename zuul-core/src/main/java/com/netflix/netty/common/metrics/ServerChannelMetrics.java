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
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.BasicGauge;
import com.netflix.servo.monitor.Gauge;
import com.netflix.servo.monitor.MonitorConfig;
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
    private final BasicCounter totalConnections;
    private final BasicCounter connectionClosed;
    private final BasicCounter connectionIdleTimeout;
    private final BasicCounter connectionErrors;
    private final BasicCounter connectionThrottled;

    public ServerChannelMetrics(String id)
    {
        super();
        
        String metricNamePrefix = "server.connections.";
        currentConnectionsGauge = new BasicGauge<>(MonitorConfig.builder(metricNamePrefix + "current").withTag("id", id).build(),
                () -> currentConnections.get() );
        DefaultMonitorRegistry.getInstance().register(currentConnectionsGauge);

        totalConnections = createCounter(metricNamePrefix + "connect", id);
        connectionErrors = createCounter(metricNamePrefix + "errors", id);
        connectionClosed = createCounter(metricNamePrefix + "close", id);
        connectionIdleTimeout = createCounter(metricNamePrefix + "idle.timeout", id);
        connectionThrottled = createCounter(metricNamePrefix + "throttled", id);
    }
    
    private static BasicCounter createCounter(String name, String id)
    {
        BasicCounter counter = new BasicCounter(MonitorConfig.builder(name).withTag("id", id).build());
        DefaultMonitorRegistry.getInstance().register(counter);
        return counter;
    }

    public static int currentConnectionCountFromChannel(Channel ch)
    {
        AtomicInteger count = ch.attr(ATTR_CURRENT_CONNS).get();
        return count == null ? 0 : count.get();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        currentConnections.incrementAndGet();
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
            currentConnections.decrementAndGet();
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
        else if (evt instanceof IdleStateEvent) {
            connectionIdleTimeout.increment();
        }

        super.userEventTriggered(ctx, evt);
    }
}
