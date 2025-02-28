/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.zuul.netty.connectionpool;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.zuul.origins.OriginName;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Justin Guerra
 * @since 2/26/25
 */
public record ConnectionPoolMetrics(Counter createNewConnCounter,
                                    Counter createConnSucceededCounter,
                                    Counter createConnFailedCounter,
                                    Counter closeConnCounter,
                                    Counter closeAbovePoolHighWaterMarkCounter,
                                    Counter closeExpiredConnLifetimeCounter,
                                    Counter requestConnCounter,
                                    Counter reuseConnCounter,
                                    Counter releaseConnCounter,
                                    Counter alreadyClosedCounter,
                                    Counter connTakenFromPoolIsNotOpen,
                                    Counter maxConnsPerHostExceededCounter,
                                    Counter closeWrtBusyConnCounter,
                                    Counter circuitBreakerClose,
                                    PercentileTimer connEstablishTimer, AtomicInteger connsInPool,
                                    AtomicInteger connsInUse) {

    public static final String METRIC_PREFIX = "connectionpool_";

    public static ConnectionPoolMetrics create(OriginName originName, Registry registry) {
        Counter createNewConnCounter = newCounter("create", originName, registry);
        Counter createConnSucceededCounter = newCounter("create_success", originName, registry);
        Counter createConnFailedCounter = newCounter("create_fail", originName, registry);

        Counter closeConnCounter = newCounter("close", originName, registry);
        Counter closeAbovePoolHighWaterMarkCounter = newCounter("closeAbovePoolHighWaterMark", originName, registry);
        Counter closeExpiredConnLifetimeCounter = newCounter("closeExpiredConnLifetime", originName, registry);
        Counter requestConnCounter = newCounter("request", originName, registry);
        Counter reuseConnCounter = newCounter("reuse", originName, registry);
        Counter releaseConnCounter = newCounter("release", originName, registry);
        Counter alreadyClosedCounter = newCounter("alreadyClosed", originName, registry);
        Counter connTakenFromPoolIsNotOpen = newCounter("fromPoolIsClosed", originName, registry);
        Counter maxConnsPerHostExceededCounter = newCounter("maxConnsPerHostExceeded", originName, registry);
        Counter closeWrtBusyConnCounter = newCounter("closeWrtBusyConnCounter", originName, registry);
        Counter circuitBreakerClose = newCounter("closeCircuitBreaker", originName, registry);

        PercentileTimer connEstablishTimer = PercentileTimer.get(
                registry, registry.createId(METRIC_PREFIX + "createTiming", "id", originName.getMetricId()));

        AtomicInteger connsInPool = newGauge("inPool", originName, registry);
        AtomicInteger connsInUse = newGauge("inUse", originName, registry);

        return new ConnectionPoolMetrics(createNewConnCounter, createConnSucceededCounter, createConnFailedCounter,
                closeConnCounter, closeAbovePoolHighWaterMarkCounter, closeExpiredConnLifetimeCounter, requestConnCounter,
                reuseConnCounter, releaseConnCounter, alreadyClosedCounter, connTakenFromPoolIsNotOpen,
                maxConnsPerHostExceededCounter, closeWrtBusyConnCounter, circuitBreakerClose, connEstablishTimer, connsInPool, connsInUse);
    }

    private static Counter newCounter(String metricName, OriginName originName, Registry registry) {
        return registry.counter(METRIC_PREFIX + metricName, "id", originName.getMetricId());
    }

    private static AtomicInteger newGauge(String metricName, OriginName originName, Registry registry) {
        return PolledMeter.using(registry)
                .withName(METRIC_PREFIX + metricName)
                .withTag("id", originName.getMetricId())
                .monitorValue(new AtomicInteger());
    }

}
