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

import com.google.common.collect.Lists;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Tag;
import com.netflix.zuul.origins.OriginName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Justin Guerra
 * @since 2/28/25
 */
class ConnectionPoolMetricsTest {

    @Test
    public void validateMetricNames() {
        DefaultRegistry registry = new DefaultRegistry();
        OriginName originName = OriginName.fromVipAndApp("whatever", "whatever");
        ConnectionPoolMetrics metrics = ConnectionPoolMetrics.create(originName, registry);

        validateCounter("connectionpool_create", metrics.createNewConnCounter());
        validateCounter("connectionpool_create_success", metrics.createConnSucceededCounter());
        validateCounter("connectionpool_create_fail", metrics.createConnFailedCounter());

        validateCounter("connectionpool_close", metrics.closeConnCounter());
        validateCounter("connectionpool_closeAbovePoolHighWaterMark", metrics.closeAbovePoolHighWaterMarkCounter());
        validateCounter("connectionpool_closeExpiredConnLifetime", metrics.closeExpiredConnLifetimeCounter());
        validateCounter("connectionpool_request", metrics.requestConnCounter());
        validateCounter("connectionpool_reuse", metrics.reuseConnCounter());
        validateCounter("connectionpool_release", metrics.releaseConnCounter());
        validateCounter("connectionpool_alreadyClosed", metrics.alreadyClosedCounter());
        validateCounter("connectionpool_fromPoolIsClosed", metrics.connTakenFromPoolIsNotOpen());
        validateCounter("connectionpool_maxConnsPerHostExceeded", metrics.maxConnsPerHostExceededCounter());
        validateCounter("connectionpool_closeWrtBusyConnCounter", metrics.closeWrtBusyConnCounter());
        validateCounter("connectionpool_closeCircuitBreaker", metrics.circuitBreakerClose());
    }

    private void validateCounter(String name, Counter counter) {
        assertEquals(name, counter.id().name());
        Map<String, String> tags = Lists.newArrayList(counter.id().tags().iterator()).stream()
                .collect(Collectors.toMap(Tag::key, Tag::value));
        assertEquals("whatever", tags.get("id"));
    }
}
