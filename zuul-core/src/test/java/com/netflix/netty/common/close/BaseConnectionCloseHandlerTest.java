/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.netty.common.close;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseConnectionCloseHandlerTest {

    private static final int PORT = 7001;

    private Registry registry;
    private RecordingCloseHandler handler;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        registry = new DefaultRegistry();
        handler = new RecordingCloseHandler(registry);
        channel = new EmbeddedChannel(handler);
        channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).set(PORT);

        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.set(CommonChannelConfigKeys.connCloseDelay, 0);
        channel.attr(BaseZuulChannelInitializer.ATTR_CHANNEL_CONFIG).set(channelConfig);
    }

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void gracefulEventIsHandledSynchronously() {
        ConnectionCloseEvent.Graceful event = new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN);

        channel.pipeline().fireUserEventTriggered(event);

        assertThat(handler.handledEvents).containsExactly(event);
    }

    @Test
    void gracefulDelayedEventIsNotHandledUntilTheScheduledTaskRuns() {
        ConnectionCloseEvent.GracefulDelayed event =
                new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1));

        channel.pipeline().fireUserEventTriggered(event);
        assertThat(handler.handledEvents).isEmpty();

        channel.runScheduledPendingTasks();

        assertThat(handler.handledEvents).containsExactly(event);
    }

    @Test
    void secondCloseEventIsIgnoredOnceFlaggedForClose() {
        ConnectionCloseEvent.Graceful first = new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN);
        ConnectionCloseEvent.Graceful second = new ConnectionCloseEvent.Graceful(CloseReason.EXPIRATION);

        channel.pipeline().fireUserEventTriggered(first);
        channel.pipeline().fireUserEventTriggered(second);

        assertThat(handler.handledEvents).containsExactly(first);
    }

    @Test
    void gracefulEventPreemptsAnAlreadyScheduledGracefulDelayedEvent() {
        ConnectionCloseEvent.GracefulDelayed delayed =
                new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1));
        ConnectionCloseEvent.Graceful immediate = new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN);

        channel.pipeline().fireUserEventTriggered(delayed);
        channel.pipeline().fireUserEventTriggered(immediate);

        assertThat(handler.handledEvents).containsExactly(immediate);

        channel.runScheduledPendingTasks();

        // the already-scheduled delayed task still runs, but the base guard suppresses it once flagged
        assertThat(handler.handledEvents).containsExactly(immediate);
    }

    @Test
    void secondGracefulDelayedEventDoesNotScheduleASecondTask() {
        ConnectionCloseEvent.GracefulDelayed first =
                new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1));
        ConnectionCloseEvent.GracefulDelayed second =
                new ConnectionCloseEvent.GracefulDelayed(CloseReason.EXPIRATION, Duration.ofMillis(1));

        channel.pipeline().fireUserEventTriggered(first);
        channel.pipeline().fireUserEventTriggered(second);
        channel.runScheduledPendingTasks();

        assertThat(handler.handledEvents).containsExactly(first);
    }

    @Test
    void delayedCloseIsNotScheduledOnAnInactiveChannel() {
        channel.close();

        channel.pipeline()
                .fireUserEventTriggered(
                        new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1)));

        assertThat((Object) handler.jitterFuture).isNull();
    }

    @Test
    void channelInactiveCancelsAPendingDelayedClose() {
        channel.pipeline()
                .fireUserEventTriggered(
                        new ConnectionCloseEvent.GracefulDelayed(CloseReason.OUT_OF_SERVICE, Duration.ofMillis(1)));
        assertThat((Object) handler.jitterFuture).isNotNull();

        // fire channelInactive directly, bypassing EmbeddedChannel#close()'s internal drain of due
        // scheduled tasks, so the cancellation itself - not a lucky ordering - is what's under test
        channel.pipeline().fireChannelInactive();

        assertThat((Object) handler.jitterFuture).isNull();
        channel.runScheduledPendingTasks();
        assertThat(handler.handledEvents).isEmpty();
    }

    @Test
    void channelInactiveCancelsAPendingCloseTimeout() {
        handler.scheduleCloseTimeout(
                () -> {
                    throw new AssertionError("cancelled close timeout must not run");
                },
                channel);
        assertThat((Object) handler.forceCloseFuture).isNotNull();

        channel.pipeline().fireChannelInactive();

        assertThat((Object) handler.forceCloseFuture).isNull();
        channel.runScheduledPendingTasks();
    }

    @Test
    void scheduleCloseTimeoutRunsTheGivenTaskOnceTheDelayElapses() {
        AtomicInteger runs = new AtomicInteger();

        handler.scheduleCloseTimeout(runs::incrementAndGet, channel);
        assertThat((Object) handler.forceCloseFuture).isNotNull();
        assertThat(runs).hasValue(0);

        channel.runScheduledPendingTasks();

        assertThat(runs).hasValue(1);
    }

    @Test
    void scheduleCloseTimeoutDoesNotScheduleASecondTaskWhileOneIsPending() {
        AtomicInteger runs = new AtomicInteger();

        handler.scheduleCloseTimeout(runs::incrementAndGet, channel);
        handler.scheduleCloseTimeout(runs::incrementAndGet, channel);
        channel.runScheduledPendingTasks();

        assertThat(runs).hasValue(1);
    }

    @Test
    void scheduleCloseTimeoutIsANoOpOnAnInactiveChannel() {
        channel.close();

        handler.scheduleCloseTimeout(
                () -> {
                    throw new AssertionError("should not run on an inactive channel");
                },
                channel);

        assertThat((Object) handler.forceCloseFuture).isNull();
    }

    @Test
    void getPortReadsTheServerLocalPortAttribute() {
        assertThat(handler.getPort(channel)).isEqualTo(PORT);
    }

    @Test
    void getPortDefaultsToNegativeOneWhenTheAttributeIsUnset() {
        EmbeddedChannel unbound = new EmbeddedChannel();
        assertThat(handler.getPort(unbound)).isEqualTo(-1);
        unbound.finishAndReleaseAll();
    }

    @Test
    void startedCounterIsRecordedWhenACloseEventIsHandled() {
        assertThat(startedCount("GRACEFUL", CloseReason.SHUTDOWN)).isZero();

        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));

        assertThat(startedCount("GRACEFUL", CloseReason.SHUTDOWN)).isEqualTo(1);
    }

    @Test
    void countHandledIsANoOpUntilFlaggedForClose() {
        handler.countHandled(PORT, "idle");
        assertThat(handledCount("GRACEFUL", CloseReason.SHUTDOWN, "idle")).isZero();

        channel.pipeline().fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN));
        handler.countHandled(PORT, "idle");

        assertThat(handledCount("GRACEFUL", CloseReason.SHUTDOWN, "idle")).isEqualTo(1);
    }

    private long startedCount(String closeType, CloseReason reason) {
        Id id = registry.createId("server.connection.close.started")
                .withTag("close_type", closeType)
                .withTag("close_reason", reason.name())
                .withTag("port", Integer.toString(PORT))
                .withTag("protocol", "test");
        return registry.counter(id).count();
    }

    private long handledCount(String closeType, CloseReason reason, String trigger) {
        Id id = registry.createId("server.connection.close.handled")
                .withTag("close_type", closeType)
                .withTag("close_reason", reason.name())
                .withTag("close_trigger", trigger)
                .withTag("port", Integer.toString(PORT))
                .withTag("protocol", "test");
        return registry.counter(id).count();
    }

    private static final class RecordingCloseHandler extends BaseConnectionCloseHandler {

        private final List<ConnectionCloseEvent> handledEvents = new ArrayList<>();

        RecordingCloseHandler(Registry registry) {
            super(registry);
        }

        @Override
        protected String getProtocol() {
            return "test";
        }

        @Override
        protected void onCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event) {
            handledEvents.add(event);
        }
    }
}
