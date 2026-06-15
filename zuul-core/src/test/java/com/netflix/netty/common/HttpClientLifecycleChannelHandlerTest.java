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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpClientLifecycleChannelHandlerTest {

    private EmbeddedChannel channel;

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
