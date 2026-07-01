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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractHttpConnectionExpiryHandlerTest {

    private static final int MAX_EXPIRY_MILLIS = 30_000;

    private static final long PAST_EXPIRY_MILLIS = Duration.ofHours(1).toMillis();

    private Clock clock;
    private CloseEventCaptor closeEvents;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        clock = mock(Clock.class);
        closeEvents = new CloseEventCaptor();
        channel = new EmbeddedChannel(closeEvents);
    }

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void responseIncrementsCount() {
        TestExpiryHandler handler = addHandler(100, MAX_EXPIRY_MILLIS);

        channel.writeOutbound(new TerminalMessage());

        assertThat(handler.getCount()).isEqualTo(1);
    }

    @Test
    void nonResponseDoesNotIncrementCount() {
        TestExpiryHandler handler = addHandler(100, MAX_EXPIRY_MILLIS);

        channel.writeOutbound("not-terminal");

        assertThat(handler.getCount()).isZero();
    }

    @Test
    void writeIsAlwaysForwardedDownstream() {
        addHandler(100, MAX_EXPIRY_MILLIS);

        TerminalMessage terminal = new TerminalMessage();
        channel.writeOutbound(terminal);
        channel.writeOutbound("not-terminal");

        TerminalMessage forwardedTerminal = channel.readOutbound();
        String forwardedNonTerminal = channel.readOutbound();
        assertThat(forwardedTerminal).isSameAs(terminal);
        assertThat(forwardedNonTerminal).isEqualTo("not-terminal");
    }

    @Test
    void connectionExpiresOnceMaxRequestsReached() {
        TestExpiryHandler handler = addHandler(2, MAX_EXPIRY_MILLIS);

        channel.writeOutbound(new TerminalMessage());
        assertThat(closeEvents.events).isEmpty();

        channel.writeOutbound(new TerminalMessage());

        assertThat(closeEvents.events).hasSize(1);
        ConnectionCloseEvent event = closeEvents.events.get(0);
        assertThat(event).isInstanceOf(ConnectionCloseEvent.Graceful.class);
        assertThat(event.reason()).isEqualTo(CloseReason.EXPIRATION);
    }

    @Test
    void connectionExpiresOncePastExpiryTime() {
        addHandler(100, MAX_EXPIRY_MILLIS);
        when(clock.millis()).thenReturn(PAST_EXPIRY_MILLIS);

        channel.writeOutbound(new TerminalMessage());

        assertThat(closeEvents.events).hasSize(1);
        assertThat(closeEvents.events.get(0).reason()).isEqualTo(CloseReason.EXPIRATION);
    }

    @Test
    void connectionDoesNotExpireWhenUnderLimits() {
        TestExpiryHandler handler = addHandler(5, MAX_EXPIRY_MILLIS);

        channel.writeOutbound(new TerminalMessage());

        assertThat(closeEvents.events).isEmpty();
        assertThat(handler.getCount()).isEqualTo(1);
    }

    @Test
    void nonResponseNeverTriggersExpiry() {
        TestExpiryHandler handler = addHandler(1, MAX_EXPIRY_MILLIS);
        when(clock.millis()).thenReturn(PAST_EXPIRY_MILLIS);

        channel.writeOutbound("not-terminal");

        assertThat(closeEvents.events).isEmpty();
        assertThat(handler.getCount()).isZero();
    }

    private TestExpiryHandler addHandler(int maxRequests, int maxExpiry) {
        TestExpiryHandler handler = new TestExpiryHandler(maxRequests, maxExpiry, clock);
        channel.pipeline().addLast(handler);
        return handler;
    }

    private static final class TestExpiryHandler extends AbstractHttpConnectionExpiryHandler {
        TestExpiryHandler(int maxRequests, int maxExpiry, Clock clock) {
            super(maxRequests, maxExpiry, clock);
        }

        @Override
        protected boolean isResponse(Object msg) {
            return msg instanceof TerminalMessage;
        }
    }

    private static final class CloseEventCaptor extends ChannelInboundHandlerAdapter {
        private final List<ConnectionCloseEvent> events = new ArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof ConnectionCloseEvent closeEvent) {
                events.add(closeEvent);
            }
            ctx.fireUserEventTriggered(evt);
        }
    }

    private static final class TerminalMessage {}
}
