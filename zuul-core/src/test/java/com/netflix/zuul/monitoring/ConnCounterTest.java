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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnCounterTest {

    @AfterEach
    void tearDown() {
        ConnCounter.clearCache();
    }

    @Test
    void record() {
        EmbeddedChannel chan = new EmbeddedChannel();
        Attrs attrs = Attrs.newInstance();
        chan.attr(Server.CONN_DIMENSIONS).set(attrs);
        Registry registry = new DefaultRegistry();
        ConnCounter counter = ConnCounter.install(chan, registry, registry.createId("foo"));

        counter.increment("start");
        counter.increment("middle");
        Attrs.newKey("bar").put(attrs, "baz");
        counter.increment("end");
        PolledMeter.update(registry);

        Gauge meter1 = registry.gauge(registry.createId("foo.start"));
        assertThat(meter1).isNotNull();
        assertThat(meter1.value()).isCloseTo(1.0, Offset.offset(0.0));

        Gauge meter2 = registry.gauge(registry.createId("foo.middle"));
        assertThat(meter2).isNotNull();
        assertThat(meter2.value()).isCloseTo(1.0, Offset.offset(0.0));

        Gauge meter3 = registry.gauge(registry.createId("foo.end", "bar", "baz"));
        assertThat(meter3).isNotNull();
        assertThat(meter3.value()).isCloseTo(1.0, Offset.offset(0.0));
    }

    @Test
    void activeConnsCount() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Attrs attrs = Attrs.newInstance();
        channel.attr(Server.CONN_DIMENSIONS).set(attrs);
        Registry registry = new DefaultRegistry();

        ConnCounter.install(channel, registry, registry.createId("foo"));

        // Dedup increments
        ConnCounter.from(channel).increment("active");
        ConnCounter.from(channel).increment("active");

        assertThat(ConnCounter.from(channel).getCurrentActiveConns()).isCloseTo(1.0, Offset.offset(0.0));
    }

    @Test
    void incrementAfterDecrementIsNotDeduped() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        Registry registry = new DefaultRegistry();
        ConnCounter counter = ConnCounter.install(chan, registry, registry.createId("foo"));

        counter.increment("tls");
        counter.decrement("tls");

        // decrement cleared the counts entry, so this increment is not deduped
        counter.increment("tls");
        PolledMeter.update(registry);
        assertThat(registry.gauge(registry.createId("foo.tls")).value()).isCloseTo(1.0, Offset.offset(0.0));
    }

    @Test
    void gaugeIsSharedAcrossChannelsWithMatchingId() {
        Registry registry = new DefaultRegistry();
        Id base = registry.createId("foo");

        EmbeddedChannel chanA = new EmbeddedChannel();
        chanA.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        ConnCounter counterA = ConnCounter.install(chanA, registry, base);

        EmbeddedChannel chanB = new EmbeddedChannel();
        chanB.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        ConnCounter counterB = ConnCounter.install(chanB, registry, base);

        counterA.increment("tls");
        counterB.increment("tls");
        Id tlsId = registry.createId("foo.tls");
        PolledMeter.update(registry);
        assertThat(registry.gauge(tlsId).value()).isCloseTo(2.0, Offset.offset(0.0));

        counterA.decrement("tls");
        PolledMeter.update(registry);
        assertThat(registry.gauge(tlsId).value()).isCloseTo(1.0, Offset.offset(0.0));
    }

    @Test
    void fromResolvesToParentChannelCounter() {
        EmbeddedChannel parent = new EmbeddedChannel();
        parent.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        Registry registry = new DefaultRegistry();
        ConnCounter parentCounter = ConnCounter.install(parent, registry, registry.createId("foo"));

        EmbeddedChannel child = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), false, false);

        assertThat(ConnCounter.from(child)).isSameAs(parentCounter);
    }

    @Test
    void installThrowsWhenCounterAlreadyPresent() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        Registry registry = new DefaultRegistry();
        ConnCounter.install(chan, registry, registry.createId("foo"));

        assertThatThrownBy(() -> ConnCounter.install(chan, registry, registry.createId("foo")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pre-existing counter");
    }

    @Test
    void fromThrowsWhenNoCounterInstalled() {
        assertThatThrownBy(() -> ConnCounter.from(new EmbeddedChannel()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no counter on channel");
    }

    @Test
    void decrementOfUnseenEventIsNoOp() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        Registry registry = new DefaultRegistry();
        ConnCounter counter = ConnCounter.install(chan, registry, registry.createId("foo"));

        assertThatCode(() -> counter.decrement("tls")).doesNotThrowAnyException();
        // no gauge was ever touched, so nothing was driven negative
        assertThat(registry.gauge(registry.createId("foo.tls")).value()).isNaN();
    }

    @Test
    void getCurrentActiveConnsIsZeroWhenNeverIncremented() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        Registry registry = new DefaultRegistry();
        ConnCounter counter = ConnCounter.install(chan, registry, registry.createId("foo"));

        assertThat(counter.getCurrentActiveConns()).isCloseTo(0.0, Offset.offset(0.0));
    }

    @Test
    void gaugeStaysCorrectWhenConnectionOutlivesMeterTtl() {
        ManualClock clock = new ManualClock();
        long ttlMillis = TimeUnit.MINUTES.toMillis(15);
        ExpiringRegistry registry = new ExpiringRegistry(clock, ttlMillis);

        EmbeddedChannel chan = new EmbeddedChannel();
        chan.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        ConnCounter counter = ConnCounter.install(chan, registry, registry.createId("foo"));

        counter.increment("tls");
        Id tlsId = registry.createId("foo.tls");
        PolledMeter.update(registry);
        assertThat(registry.gauge(tlsId).value()).isCloseTo(1.0, Offset.offset(0.0));

        // Connection stays open past the meter TTL, then the publish loop evicts the idle gauge.
        clock.setWallTime(ttlMillis + 1);
        registry.removeExpiredMeters();

        counter.decrement("tls");
        PolledMeter.update(registry);

        assertThat(registry.gauge(tlsId).value()).isCloseTo(0.0, Offset.offset(0.0));
    }

    private static final class ExpiringRegistry extends AbstractRegistry {
        private final long ttlMillis;

        ExpiringRegistry(Clock clock, long ttlMillis) {
            super(clock);
            this.ttlMillis = ttlMillis;
        }

        @Override
        public void removeExpiredMeters() {
            super.removeExpiredMeters();
        }

        @Override
        protected Gauge newGauge(Id id) {
            return new ExpiringGauge(clock(), id, ttlMillis);
        }

        @Override
        protected Gauge newMaxGauge(Id id) {
            return newGauge(id);
        }

        @Override
        protected Counter newCounter(Id id) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected DistributionSummary newDistributionSummary(Id id) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Timer newTimer(Id id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ExpiringGauge implements Gauge {
        private final Clock clock;
        private final Id id;
        private final long ttlMillis;
        private double value;
        private long lastUpdated;

        ExpiringGauge(Clock clock, Id id, long ttlMillis) {
            this.clock = clock;
            this.id = id;
            this.ttlMillis = ttlMillis;
            this.lastUpdated = clock.wallTime();
        }

        @Override
        public Id id() {
            return id;
        }

        @Override
        public Iterable<Measurement> measure() {
            return Collections.singletonList(new Measurement(id, clock.wallTime(), value));
        }

        @Override
        public boolean hasExpired() {
            return clock.wallTime() - lastUpdated > ttlMillis;
        }

        @Override
        public void set(double v) {
            value = v;
            lastUpdated = clock.wallTime();
        }

        @Override
        public double value() {
            return value;
        }
    }
}
