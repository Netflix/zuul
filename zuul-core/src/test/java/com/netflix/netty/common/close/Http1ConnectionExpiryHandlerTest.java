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
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http1ConnectionExpiryHandlerTest {

    private Http1ConnectionExpiryHandler handler;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        handler = new Http1ConnectionExpiryHandler(100, Integer.MAX_VALUE);
        channel = new EmbeddedChannel(handler);
    }

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void httpResponseIsTerminal() {
        channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        assertThat(handler.requestCount).isEqualTo(1);
    }

    @Test
    void httpContentIsNotTerminal() {
        channel.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(handler.requestCount).isZero();
    }
}
