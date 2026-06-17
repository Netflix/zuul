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

package com.netflix.netty.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.netty.common.HttpLifecycleChannelHandler.State;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpClientLifecycleChannelHandlerTest {

    private EmbeddedChannel channel;

    /** Collects all CompleteEvents fired as user events on the pipeline. */
    private static final class CompleteEventCollector extends ChannelInboundHandlerAdapter {
        final List<CompleteEvent> events = new ArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof CompleteEvent ce) {
                events.add(ce);
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel(HttpClientLifecycleChannelHandler.OUTBOUND_CHANNEL_HANDLER);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    void lastContentPendingAfterRequestHeadersOnly() {
        channel.writeOutbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo"));
        channel.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[] {1, 2, 3})));

        assertThat(channel.attr(HttpClientLifecycleChannelHandler.ATTR_OUTBOUND_LAST_CONTENT_PENDING)
                        .get())
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void lastContentPendingClearedAfterLastHttpContent() {
        channel.writeOutbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo"));
        channel.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[] {1, 2, 3})));
        channel.writeOutbound(new DefaultLastHttpContent());

        assertThat(channel.attr(HttpClientLifecycleChannelHandler.ATTR_OUTBOUND_LAST_CONTENT_PENDING)
                        .get())
                .isNull();
    }

    @Test
    void sessionCompleteNotFiredAfter1xxResponse() {
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel inboundChannel =
                new EmbeddedChannel(HttpClientLifecycleChannelHandler.INBOUND_CHANNEL_HANDLER, collector);
        inboundChannel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        // Netty decodes a 1xx from the origin as two separate events
        inboundChannel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EARLY_HINTS));
        inboundChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(collector.events).isEmpty();
        inboundChannel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteFiredAfterFinalResponseFollowing1xx() {
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel inboundChannel =
                new EmbeddedChannel(HttpClientLifecycleChannelHandler.INBOUND_CHANNEL_HANDLER, collector);
        inboundChannel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        // 1xx pair — must NOT trigger SESSION_COMPLETE
        inboundChannel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EARLY_HINTS));
        inboundChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        assertThat(collector.events).isEmpty();

        // Final 200 response — must trigger SESSION_COMPLETE exactly once
        inboundChannel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        inboundChannel.writeInbound(new DefaultLastHttpContent());
        assertThat(collector.events).hasSize(1);
        assertThat(collector.events.getFirst().getReason()).isEqualTo(CompleteReason.SESSION_COMPLETE);
        inboundChannel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteFiredAfter101SwitchingProtocols() {
        // 101 is numerically a 1xx but is terminal (e.g. a WebSocket upgrade), so it must complete the session.
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel inboundChannel =
                new EmbeddedChannel(HttpClientLifecycleChannelHandler.INBOUND_CHANNEL_HANDLER, collector);
        inboundChannel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        inboundChannel.writeInbound(
                new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));
        inboundChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(collector.events).hasSize(1);
        assertThat(collector.events.getFirst().getReason()).isEqualTo(CompleteReason.SESSION_COMPLETE);
        inboundChannel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteFiredForNormalFinalResponse() {
        CompleteEventCollector collector = new CompleteEventCollector();
        EmbeddedChannel inboundChannel =
                new EmbeddedChannel(HttpClientLifecycleChannelHandler.INBOUND_CHANNEL_HANDLER, collector);
        inboundChannel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(State.STARTED);

        inboundChannel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        inboundChannel.writeInbound(new DefaultLastHttpContent());

        assertThat(collector.events).hasSize(1);
        assertThat(collector.events.getFirst().getReason()).isEqualTo(CompleteReason.SESSION_COMPLETE);
        inboundChannel.finishAndReleaseAll();
    }

    @Test
    void lastContentPendingResetsOnNewRequest() {
        channel.writeOutbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"));
        channel.writeOutbound(new DefaultLastHttpContent());
        assertThat(channel.attr(HttpClientLifecycleChannelHandler.ATTR_OUTBOUND_LAST_CONTENT_PENDING)
                        .get())
                .isNull();

        // mimic the response side completing the previous request so a second start event will fire
        channel.attr(HttpLifecycleChannelHandler.ATTR_STATE).set(null);

        channel.writeOutbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/second"));
        assertThat(channel.attr(HttpClientLifecycleChannelHandler.ATTR_OUTBOUND_LAST_CONTENT_PENDING)
                        .get())
                .isEqualTo(Boolean.TRUE);
    }
}
