/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul.monitoring;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.Attrs.Key;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A timer for connection stats.  Not thread-safe.
 */
public final class ConnTimer {

    private static final AttributeKey<ConnTimer> CONN_TIMER = AttributeKey.newInstance("zuul.conntimer");

    private static final Duration MIN_CONN_TIMING = Duration.ofNanos(512);
    private static final Duration MAX_CONN_TIMING = Duration.ofDays(366);

    private final Registry registry;
    private final Channel chan;
    // TODO(carl-mastrangelo): make this changable.
    private final Id metricBase;

    private final Map<String, Long> timings = new LinkedHashMap<>();

    private ConnTimer(Registry registry, Channel chan, Id metricBase) {
        this.registry = Objects.requireNonNull(registry);
        this.chan = Objects.requireNonNull(chan);
        this.metricBase = Objects.requireNonNull(metricBase);
    }

    public static ConnTimer install(Channel chan, Registry registry, Id metricBase) {
        ConnTimer timer = new ConnTimer(registry, chan, metricBase);
        if (!chan.attr(CONN_TIMER).compareAndSet(null, timer)) {
            throw new IllegalStateException("pre-existing timer already present");
        }
        return timer;
    }

    public static ConnTimer from(Channel chan) {
        Objects.requireNonNull(chan);
        ConnTimer timer = chan.attr(CONN_TIMER).get();
        if (timer != null) {
            return timer;
        }
        if (chan.parent() != null && (timer = chan.parent().attr(CONN_TIMER).get()) != null) {
            return timer;
        }
        throw new IllegalStateException("no timer on channel");
    }

    public void record(Long now, String event) {
        if (timings.containsKey(event)) {
            return;
        }
        Attrs dims = chan.attr(Server.CONN_DIMENSIONS).get();
        Set<Key<?>> dimKeys = dims.keySet();
        Map<String, String> dimTags = new HashMap<>(dimKeys.size());
        for (Key<?> key : dims.keySet()) {
            dimTags.put(key.name(), String.valueOf(key.get(dims)));
        }

        // Note: this is effectively O(n^2) because it will be called for each event in the connection
        // setup.  It should be bounded to at most 10 or so.
        timings.forEach((k, v) -> {
            long durationNanos = now - v;
            if (durationNanos == 0) {
                // This may happen if an event is double listed, or if the timer is not accurate enough to record
                // it.
                return;
            }
            PercentileTimer.builder(registry)
                    .withId(metricBase.withTags(dimTags).withTags("from", k, "to", event))
                    .withRange(MIN_CONN_TIMING, MAX_CONN_TIMING)
                    .build()
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        });
        timings.put(event, now);
    }
}
