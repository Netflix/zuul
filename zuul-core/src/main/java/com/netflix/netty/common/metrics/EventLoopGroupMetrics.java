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

import com.netflix.spectator.api.Registry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * User: michaels@netflix.com
 * Date: 2/7/17
 * Time: 3:17 PM
 */
@Singleton
public class EventLoopGroupMetrics
{
    private final ThreadLocal<EventLoopMetrics> metricsForCurrentThread;
    private final Map<Thread, EventLoopMetrics> byEventLoop = new HashMap<>();
    private final Registry registry;

    @Inject
    public EventLoopGroupMetrics(Registry registry)
    {
        this.registry = registry;
        this.metricsForCurrentThread = ThreadLocal.withInitial(() ->
        {
            String name = nameForCurrentEventLoop();
            EventLoopMetrics metrics = new EventLoopMetrics(registry, name);
            byEventLoop.put(Thread.currentThread(), metrics);
            return metrics;
        });
    }

    public Map<Thread, Integer> connectionsPerEventLoop()
    {
        Map<Thread, Integer> map = new HashMap<>(byEventLoop.size());
        for (Map.Entry<Thread, EventLoopMetrics> entry : byEventLoop.entrySet())
        {
            map.put(entry.getKey(), entry.getValue().currentConnectionsCount());
        }
        return map;
    }

    public Map<Thread, Integer> httpRequestsPerEventLoop()
    {
        Map<Thread, Integer> map = new HashMap<>(byEventLoop.size());
        for (Map.Entry<Thread, EventLoopMetrics> entry : byEventLoop.entrySet())
        {
            map.put(entry.getKey(), entry.getValue().currentHttpRequestsCount());
        }
        return map;
    }

    public EventLoopMetrics getForCurrentEventLoop()
    {
        return metricsForCurrentThread.get();
    }

    private static String nameForCurrentEventLoop()
    {
        // We're relying on the knowledge that we name the eventloop threads consistently.
        String threadName = Thread.currentThread().getName();
        String parts[] = threadName.split("-ClientToZuulWorker-");
        if (parts.length == 2) {
            return parts[1];
        }
        return threadName;
    }

    interface EventLoopInfo
    {
        int currentConnectionsCount();
        int currentHttpRequestsCount();
    }
}
