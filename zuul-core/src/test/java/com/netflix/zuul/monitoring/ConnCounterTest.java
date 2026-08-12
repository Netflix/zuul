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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnCounterTest {

    private Registry registry;
    private Attrs connDimensions;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        registry = new DefaultRegistry();
        connDimensions = Attrs.newInstance();
        channel = new EmbeddedChannel();
        channel.attr(Server.CONN_DIMENSIONS).set(connDimensions);
    }

    @AfterEach
    void tearDown() {
        ConnCounter.clearCache();
    }

    @Test
    void record() {
        ConnCounter counter = install();

        counter.increment("start");
        counter.increment("middle");
        Attrs.newKey("bar").put(connDimensions, "baz");
        counter.increment("end");
        PolledMeter.update(registry);

        assertThat(registry.gauge(registry.createId("foo.start")).value()).isEqualTo(1.0);
        assertThat(registry.gauge(registry.createId("foo.middle")).value()).isEqualTo(1.0);
        assertThat(registry.gauge(registry.createId("foo.end", "bar", "baz")).value())
                .isEqualTo(1.0);
    }

    @Test
    void duplicateIncrementIsDeduped() {
        install();

        ConnCounter.from(channel).increment("active");
        ConnCounter.from(channel).increment("active");
        PolledMeter.update(registry);

        assertThat(ConnCounter.from(channel).getCurrentActiveConns()).isEqualTo(1.0);
        assertThat(registry.gauge(registry.createId("foo.active")).value()).isEqualTo(1.0);
    }

    @Test
    void incrementMergesExtraDimensionsOverConnDimensions() {
        Attrs.newKey("proto").put(connDimensions, "h2");
        Attrs.newKey("cipher").put(connDimensions, "unknown");
        ConnCounter counter = install();

        Attrs extraDimensions = Attrs.newInstance();
        Attrs.newKey("cipher").put(extraDimensions, "TLS_AES_128_GCM_SHA256");
        counter.increment("tls", extraDimensions);
        PolledMeter.update(registry);

        assertThat(registry.gauge(registry.createId("foo.tls", "proto", "h2", "cipher", "TLS_AES_128_GCM_SHA256"))
                        .value())
                .isEqualTo(1.0);
    }

    @Test
    void incrementAfterDecrementIsNotDeduped() {
        ConnCounter counter = install();

        counter.increment("tls");
        counter.decrement("tls");
        counter.increment("tls");
        PolledMeter.update(registry);

        assertThat(registry.gauge(registry.createId("foo.tls")).value()).isEqualTo(1.0);
    }

    @Test
    void gaugeIsSharedAcrossChannelsWithMatchingId() {
        ConnCounter counterA = install();
        ConnCounter counterB = installOnNewChannel();

        counterA.increment("tls");
        counterB.increment("tls");
        Id tlsId = registry.createId("foo.tls");
        PolledMeter.update(registry);
        assertThat(registry.gauge(tlsId).value()).isEqualTo(2.0);

        counterA.decrement("tls");
        PolledMeter.update(registry);
        assertThat(registry.gauge(tlsId).value()).isEqualTo(1.0);
    }

    @Test
    void reincrementIsNotSuppressedWhileAnotherConnectionHoldsTheEvent() {
        ConnCounter counterA = install();
        ConnCounter counterB = installOnNewChannel();

        counterA.increment("tls");
        counterB.increment("tls");
        counterA.decrement("tls");
        counterA.increment("tls");
        PolledMeter.update(registry);

        assertThat(registry.gauge(registry.createId("foo.tls")).value()).isEqualTo(2.0);
    }

    @Test
    void doubleDecrementDoesNotConsumeAnotherConnectionsCount() {
        ConnCounter counterA = install();
        ConnCounter counterB = installOnNewChannel();

        counterA.increment("tls");
        counterB.increment("tls");
        counterA.decrement("tls");
        counterA.decrement("tls");
        PolledMeter.update(registry);

        assertThat(registry.gauge(registry.createId("foo.tls")).value()).isEqualTo(1.0);
    }

    @Test
    void decrementUsesTheIdCapturedAtIncrementTime() {
        ConnCounter counter = install();

        counter.increment("active");
        // callers stamp SELF_CLOSE into the conn dimensions just before decrementing
        Attrs.newKey("selfclose").put(connDimensions, "true");
        counter.decrement("active");
        PolledMeter.update(registry);

        assertThat(registry.gauge(registry.createId("foo.active")).value()).isEqualTo(0.0);
        assertThat(registry.get(registry.createId("foo.active", "selfclose", "true")))
                .isNull();
    }

    @Test
    void countsFromDifferentEventLoopsSumIntoOneGauge() throws Exception {
        ExecutorService loopA = Executors.newSingleThreadExecutor();
        ExecutorService loopB = Executors.newSingleThreadExecutor();

        try {
            loopA.submit(() -> installOnNewChannel().increment("tls")).get(5, TimeUnit.SECONDS);
            loopB.submit(() -> installOnNewChannel().increment("tls")).get(5, TimeUnit.SECONDS);
            PolledMeter.update(registry);

            assertThat(registry.gauge(registry.createId("foo.tls")).value()).isEqualTo(2.0);
        } finally {
            clearCacheAndShutdown(loopA);
            clearCacheAndShutdown(loopB);
        }
    }

    @Test
    void fromResolvesToParentChannelCounter() {
        ConnCounter parentCounter = install();

        EmbeddedChannel child = new EmbeddedChannel(channel, DefaultChannelId.newInstance(), false, false);

        assertThat(ConnCounter.from(child)).isSameAs(parentCounter);
    }

    @Test
    void installThrowsWhenCounterAlreadyPresent() {
        install();

        assertThatThrownBy(this::install)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pre-existing counter");
    }

    @Test
    void fromThrowsWhenNoCounterInstalled() {
        assertThatThrownBy(() -> ConnCounter.from(channel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no counter on channel");
    }

    @Test
    void decrementOfUnseenEventIsNoOp() {
        ConnCounter counter = install();

        assertThatCode(() -> counter.decrement("tls")).doesNotThrowAnyException();
        assertThat(registry.stream()).isEmpty();
    }

    @Test
    void getCurrentActiveConnsIsZeroWhenNeverIncremented() {
        assertThat(install().getCurrentActiveConns()).isEqualTo(0.0);
    }

    @Test
    void gaugeStaysCorrectWhenConnectionOutlivesMeterTtl() {
        ManualClock clock = new ManualClock();
        long ttlMillis = TimeUnit.MINUTES.toMillis(15);
        ExpiringRegistry expiringRegistry = new ExpiringRegistry(clock, ttlMillis);
        ConnCounter counter = ConnCounter.install(channel, expiringRegistry, expiringRegistry.createId("foo"));

        counter.increment("tls");
        PolledMeter.update(expiringRegistry);

        // PolledMeter keeps writing to the Gauge it captured at registration, so hold that instance - once the
        // registry evicts it, expiringRegistry.gauge(...) mints a fresh one whose value defaults to 0.0.
        Gauge polled = expiringRegistry.gauge(expiringRegistry.createId("foo.tls"));
        assertThat(polled.value()).isEqualTo(1.0);

        // Connection stays open past the meter TTL, then the publish loop evicts the idle gauge.
        clock.setWallTime(ttlMillis + 1);
        expiringRegistry.removeExpiredMeters();

        counter.decrement("tls");
        PolledMeter.update(expiringRegistry);

        assertThat(polled.value()).isEqualTo(0.0);
    }

    private ConnCounter install() {
        return ConnCounter.install(channel, registry, registry.createId("foo"));
    }

    /**
     * Installs a counter on a second connection sharing the registry, for tests that need two channels.
     */
    private ConnCounter installOnNewChannel() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        return ConnCounter.install(chan, registry, registry.createId("foo"));
    }

    private static void clearCacheAndShutdown(ExecutorService loop) throws Exception {
        loop.submit(ConnCounter::clearCache).get(5, TimeUnit.SECONDS);
        loop.shutdown();
        assertThat(loop.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
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
