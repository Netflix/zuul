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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import com.netflix.netty.common.HttpLifecycleChannelHandler;

/**
 * User: michaels@netflix.com
 * Date: 2/6/17
 * Time: 2:21 PM
 */

public class PerEventLoopMetricsChannelHandler
{
    private static final AttributeKey<Object> ATTR_REQ_INFLIGHT = AttributeKey.newInstance("_eventloop_metrics_inflight");
    private static final Object INFLIGHT = "eventloop_is_inflight";

    private final EventLoopGroupMetrics groupMetrics;

    public PerEventLoopMetricsChannelHandler(EventLoopGroupMetrics groupMetrics)
    {
        this.groupMetrics = groupMetrics;
    }

    @ChannelHandler.Sharable
    public class Connections extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception
        {
            groupMetrics.getForCurrentEventLoop().incrementCurrentConnections();
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception
        {
            try {
                super.channelInactive(ctx);
            }
            finally {
                groupMetrics.getForCurrentEventLoop().decrementCurrentConnections();
            }
        }
    }

    @ChannelHandler.Sharable
    public class HttpRequests extends ChannelInboundHandlerAdapter
    {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
        {
            if (evt instanceof HttpLifecycleChannelHandler.StartEvent) {
                incrementCurrentRequestsInFlight(ctx);
            }
            else if (evt instanceof HttpLifecycleChannelHandler.CompleteEvent) {
                decrementCurrentRequestsIfOneInflight(ctx);
            }

            super.userEventTriggered(ctx, evt);
        }

        private void incrementCurrentRequestsInFlight(ChannelHandlerContext ctx)
        {
            groupMetrics.getForCurrentEventLoop().incrementCurrentRequests();
            ctx.channel().attr(ATTR_REQ_INFLIGHT).set(INFLIGHT);
        }

        private void decrementCurrentRequestsIfOneInflight(ChannelHandlerContext ctx)
        {
            if (ctx.channel().attr(ATTR_REQ_INFLIGHT).getAndRemove() != null) {
                groupMetrics.getForCurrentEventLoop().decrementCurrentRequests();
            }
        }
    }
}
