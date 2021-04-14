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

import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import com.netflix.netty.common.HttpLifecycleChannelHandler;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: michaels@netflix.com
 * Date: 4/14/16
 * Time: 3:51 PM
 */
@ChannelHandler.Sharable
public class HttpMetricsChannelHandler extends ChannelInboundHandlerAdapter
{
    private static final AttributeKey<Object> ATTR_REQ_INFLIGHT = AttributeKey.newInstance("_httpmetrics_inflight");
    private static final Object INFLIGHT = "is_inflight";
    private static final AttributeKey<AtomicInteger> ATTR_CURRENT_REQS = AttributeKey.newInstance("_server_http_current_count");

    private final AtomicInteger currentRequests = new AtomicInteger(0);

    private final Registry registry;
    private final Gauge currentRequestsGauge;
    private final Counter unSupportedPipeliningCounter;

    public HttpMetricsChannelHandler(Registry registry, String name, String id)
    {
        super();

        this.registry = registry;

        this.currentRequestsGauge = this.registry.gauge(this.registry.createId(name + ".http.requests.current", "id", id));
        this.unSupportedPipeliningCounter = this.registry.counter(name + ".http.requests.pipelining.dropped", "id", id);
    }

    public static int getInflightRequestCountFromChannel(Channel ch)
    {
        AtomicInteger current = ch.attr(ATTR_CURRENT_REQS).get();
        return current == null ? 0 : current.get();
    }

    public int getInflightRequestsCount()
    {
        return currentRequests.get();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        // Store a ref to the count of current inflight requests onto this channel. So that
        // other code can query it using getInflightRequestCountFromChannel().
        ctx.channel().attr(ATTR_CURRENT_REQS).set(currentRequests);

        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        if (evt instanceof HttpServerLifecycleChannelHandler.StartEvent) {
            incrementCurrentRequestsInFlight(ctx);
        }
        else if (evt instanceof HttpServerLifecycleChannelHandler.CompleteEvent && ((CompleteEvent) evt).getReason() == CompleteReason.PIPELINE_REJECT ) {
            unSupportedPipeliningCounter.increment();
        }
        else if (evt instanceof HttpServerLifecycleChannelHandler.CompleteEvent) {
            decrementCurrentRequestsIfOneInflight(ctx);
        }
        super.userEventTriggered(ctx, evt);
    }

    private void incrementCurrentRequestsInFlight(ChannelHandlerContext ctx)
    {
        currentRequestsGauge.set(currentRequests.incrementAndGet());
        ctx.channel().attr(ATTR_REQ_INFLIGHT).set(INFLIGHT);
    }

    private void decrementCurrentRequestsIfOneInflight(ChannelHandlerContext ctx)
    {
        if (ctx.channel().attr(ATTR_REQ_INFLIGHT).getAndSet(null) != null) {
            currentRequestsGauge.set(currentRequests.decrementAndGet());
        }
    }
}
