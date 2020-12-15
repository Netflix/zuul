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

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.Attrs.Key;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A counter for connection stats.  Not thread-safe.
 */
public final class ConnCounter {

    private static final Logger logger = LoggerFactory.getLogger(ConnCounter.class);

    private static final AttributeKey<ConnCounter> CONN_COUNTER = AttributeKey.newInstance("zuul.conncounter");

    private final Registry registry;
    private final Channel chan;
    private final Id metricBase;

    private String lastCountKey;

    private final Map<String, Gauge> counts = new HashMap<>();

    private ConnCounter(Registry registry, Channel chan, Id metricBase) {
        this.registry = Objects.requireNonNull(registry);
        this.chan = Objects.requireNonNull(chan);
        this.metricBase = Objects.requireNonNull(metricBase);
    }

    public static ConnCounter install(Channel chan, Registry registry, Id metricBase) {
        ConnCounter counter = new ConnCounter(registry, chan, metricBase);
        if (!chan.attr(CONN_COUNTER).compareAndSet(null, counter)) {
            throw new IllegalStateException("pre-existing counter already present");
        }
        return counter;
    }

    public static ConnCounter from(Channel chan) {
        Objects.requireNonNull(chan);
        ConnCounter counter = chan.attr(CONN_COUNTER).get();
        if (counter != null) {
            return counter;
        }
        if (chan.parent() != null && (counter = chan.parent().attr(CONN_COUNTER).get()) != null) {
            return counter;
        }
        throw new IllegalStateException("no counter on channel");
    }

    public void increment(String event) {
        Objects.requireNonNull(event);
        if (counts.containsKey(event)) {
            // TODO(carl-mastrangelo): make this throw IllegalStateException after verifying this doesn't happen.
            logger.warn("Duplicate conn counter increment {}", event);
            return;
        }
        Attrs dims = chan.attr(Server.CONN_DIMENSIONS).get();
        Set<Key<?>> dimKeys = dims.keySet();
        Map<String, String> dimTags = new HashMap<>(dimKeys.size());
        for (Key<?> key : dims.keySet()) {
            dimTags.put(key.name(), String.valueOf(key.get(dims)));
        }
        dimTags.put("from", lastCountKey != null ? lastCountKey : "nascent");
        lastCountKey = event;
        Id id = registry.createId(metricBase.name() + '.' + event).withTags(metricBase.tags()).withTags(dimTags);
        Gauge gauge = registry.gauge(id);
        synchronized (gauge) {
            double current = gauge.value();
            gauge.set(Double.isNaN(current) ? 1 : current + 1);
        }
        counts.put(event, gauge);
    }

    public void decrement(String event) {
        Objects.requireNonNull(event);
        Gauge gauge = counts.remove(event);
        if (gauge == null) {
            // TODO(carl-mastrangelo): make this throw IllegalStateException after verifying this doesn't happen.
            logger.warn("Missing conn counter increment {}", event);
            return;
        }
        synchronized (gauge) {
            assert !Double.isNaN(gauge.value());
            gauge.set(gauge.value() - 1);
        }
    }
}
