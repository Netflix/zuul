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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http2ConnectionExpiryHandlerTest {

    private Http2ConnectionExpiryHandler handler;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        handler = new Http2ConnectionExpiryHandler(100, Integer.MAX_VALUE);
        channel = new EmbeddedChannel(handler);
    }

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void endStreamHeadersFrameIsTerminal() {
        channel.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true));

        assertThat(handler.requestCount).isEqualTo(1);
    }

    @Test
    void nonEndStreamHeadersFrameIsNotTerminal() {
        channel.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), false));

        assertThat(handler.requestCount).isZero();
    }

    @Test
    void endStreamDataFrameIsTerminal() {
        channel.writeOutbound(new DefaultHttp2DataFrame(true));

        assertThat(handler.requestCount).isEqualTo(1);
    }

    @Test
    void nonEndStreamDataFrameIsNotTerminal() {
        channel.writeOutbound(new DefaultHttp2DataFrame(false));

        assertThat(handler.requestCount).isZero();
    }

    @Test
    void otherFrameIsNotTerminal() {
        channel.writeOutbound("not-a-frame");

        assertThat(handler.requestCount).isZero();
    }
}
