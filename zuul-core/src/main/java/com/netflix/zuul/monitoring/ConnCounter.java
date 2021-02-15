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
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A counter for connection stats.  Not thread-safe.
 */
public final class ConnCounter {

    private static final Logger logger = LoggerFactory.getLogger(ConnCounter.class);

    private static final AttributeKey<ConnCounter> CONN_COUNTER = AttributeKey.newInstance("zuul.conncounter");

    private static final int LOCK_COUNT = 256;
    private static final int LOCK_MASK = LOCK_COUNT - 1;

    private static final Attrs EMPTY = Attrs.newInstance();

    /**
     * An array of locks to guard the gauges.   This is the same as Guava's Striped, but avoids the dep.
     * <p>
     * This can be removed after https://github.com/Netflix/spectator/issues/862 is fixed.
     */
    private static final Object[] locks = new Object[LOCK_COUNT];

    static {
        assert (LOCK_COUNT & LOCK_MASK) == 0;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

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
        increment(event, EMPTY);
    }

    public void increment(String event, Attrs extraDimensions) {
        Objects.requireNonNull(event);
        Objects.requireNonNull(extraDimensions);
        if (counts.containsKey(event)) {
            // TODO(carl-mastrangelo): make this throw IllegalStateException after verifying this doesn't happen.
            logger.warn("Duplicate conn counter increment {}", event);
            return;
        }
        Attrs connDims = chan.attr(Server.CONN_DIMENSIONS).get();
        Map<String, String> dimTags = new HashMap<>(connDims.size() + extraDimensions.size());

        connDims.forEach((k, v) -> dimTags.put(k.name(), String.valueOf(v)));
        extraDimensions.forEach((k, v) -> dimTags.put(k.name(), String.valueOf(v)));

        dimTags.put("from", lastCountKey != null ? lastCountKey : "nascent");
        lastCountKey = event;
        Id id = registry.createId(metricBase.name() + '.' + event).withTags(metricBase.tags()).withTags(dimTags);
        Gauge gauge = registry.gauge(id);

        synchronized (getLock(id)) {
            double current = gauge.value();
            gauge.set(Double.isNaN(current) ? 1 : current + 1);
        }
        counts.put(event, gauge);
    }

    public double getCurrentActiveConns() {
        return counts.containsKey("active") ? counts.get("active").value() : 0.0;
    }

    public void decrement(String event) {
        Objects.requireNonNull(event);
        Gauge gauge = counts.remove(event);
        if (gauge == null) {
            // TODO(carl-mastrangelo): make this throw IllegalStateException after verifying this doesn't happen.
            logger.warn("Missing conn counter increment {}", event);
            return;
        }
        synchronized (getLock(gauge.id())) {
            // Noop gauges break this assertion in tests, but the type is package private.   Check to make sure
            // the gauge has a value, or by implementation cannot have a value.
            assert !Double.isNaN(gauge.value())
                    || gauge.getClass().getName().equals("com.netflix.spectator.api.NoopGauge");
            gauge.set(gauge.value() - 1);
        }
    }

    // This is here to pick the correct lock stripe.   This avoids multiple threads synchronizing on the
    // same lock in the common case.   This can go away once there is an atomic gauge update implemented
    // in spectator.
    private static Object getLock(Id id) {
        return locks[id.hashCode() & LOCK_MASK];
    }
}
