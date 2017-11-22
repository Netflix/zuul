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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: michaels@netflix.com
 * Date: 2/7/17
 * Time: 3:18 PM
 */
public class EventLoopMetrics implements EventLoopGroupMetrics.EventLoopInfo
{
    private final String name;
    public final AtomicInteger currentRequests = new AtomicInteger(0);
    public final AtomicInteger currentConnections = new AtomicInteger(0);

    private final Registry registry;
    private final Id currentRequestsId;
    private final Id currentConnectionsId;

    public EventLoopMetrics(Registry registry, String eventLoopName)
    {
        this.name = eventLoopName;

        this.registry = registry;
        this.currentRequestsId = this.registry.createId("server.eventloop.http.requests.current");
        this.currentConnectionsId = this.registry.createId("server.eventloop.connections.current");
    }

    @Override
    public int currentConnectionsCount()
    {
        return currentConnections.get();
    }

    @Override
    public int currentHttpRequestsCount()
    {
        return currentRequests.get();
    }

    public void incrementCurrentRequests()
    {
        int value = this.currentRequests.incrementAndGet();
        updateGauge(currentRequestsId, value);
    }

    public void decrementCurrentRequests()
    {
        int value = this.currentRequests.decrementAndGet();
        updateGauge(currentRequestsId, value);
    }

    public void incrementCurrentConnections()
    {
        int value = this.currentConnections.incrementAndGet();
        updateGauge(currentConnectionsId, value);
    }

    public void decrementCurrentConnections()
    {
        int value = this.currentConnections.decrementAndGet();
        updateGauge(currentConnectionsId, value);
    }

    private void updateGauge(Id gaugeId, int value)
    {
        registry.gauge(gaugeId.withTag("eventloop", name)).set(value);
    }
}
