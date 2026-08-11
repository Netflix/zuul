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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A counter for connection stats.  Not thread-safe.
 */
@NullMarked
public final class ConnCounter {

    private static final Logger logger = LoggerFactory.getLogger(ConnCounter.class);

    private static final AttributeKey<@Nullable ConnCounter> CONN_COUNTER =
            AttributeKey.newInstance("zuul.conncounter");

    private static final Attrs EMPTY = Attrs.newInstance();

    private static final ThreadLocal<Map<Id, AtomicInteger>> PER_EVENT_LOOP_COUNTERS =
            ThreadLocal.withInitial(HashMap::new);

    private final Registry registry;
    private final Channel chan;
    private final Id metricBase;
    private final Map<String, Id> eventToIdLookup;

    private ConnCounter(Registry registry, Channel chan, Id metricBase) {
        this.registry = Objects.requireNonNull(registry);
        this.chan = Objects.requireNonNull(chan);
        this.metricBase = Objects.requireNonNull(metricBase);
        this.eventToIdLookup = new HashMap<>();
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
        if (eventToIdLookup.containsKey(event)) {
            // TODO(carl-mastrangelo): make this throw IllegalStateException after verifying this doesn't happen.
            logger.warn("Duplicate conn counter increment {}", event);
            return;
        }
        Attrs connDims = chan.attr(Server.CONN_DIMENSIONS).get();
        Map<String, String> dimTags = new HashMap<>(connDims.size() + extraDimensions.size());

        connDims.forEach((k, v) -> dimTags.put(k.name(), String.valueOf(v)));
        extraDimensions.forEach((k, v) -> dimTags.put(k.name(), String.valueOf(v)));

        Id id = registry.createId(metricBase.name() + '.' + event)
                .withTags(metricBase.tags())
                .withTags(dimTags);

        // PER_EVENT_LOOP_COUNTERS exists to reduce the number of PolledMeters for a given Id to 1 per event loop
        // instead of 1 per connection
        AtomicInteger count = PER_EVENT_LOOP_COUNTERS.get().computeIfAbsent(id, key -> {
            AtomicInteger counter = new AtomicInteger();
            PolledMeter.using(registry).withId(key).monitorValue(counter);
            return counter;
        });
        count.incrementAndGet();
        eventToIdLookup.put(event, id);
    }

    public double getCurrentActiveConns() {
        Id id = eventToIdLookup.get("active");
        if (id == null) {
            return 0.0;
        }

        AtomicInteger count = PER_EVENT_LOOP_COUNTERS.get().get(id);
        return count == null ? 0.0 : count.get();
    }

    public void decrement(String event) {
        Objects.requireNonNull(event);
        Id tags = eventToIdLookup.get(event);

        if (tags == null) {
            logger.warn("Missing conn counter increment {}", event);
            return;
        }

        PER_EVENT_LOOP_COUNTERS.get().computeIfPresent(tags, (k, v) -> {
            int count = v.decrementAndGet();
            if (count <= 0) {
                eventToIdLookup.remove(event);
                return null;
            } else {
                return v;
            }
        });
    }

    @VisibleForTesting
    static void clearCache() {
        PER_EVENT_LOOP_COUNTERS.get().clear();
    }
}
