/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.timeouts;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.netflix.spectator.api.Counter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.channels.ClosedChannelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpHeadersTimeoutHandlerTest {
    @Mock
    private Counter timeoutCounter;

    @Test
    public void testTimeout() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpHeadersTimeoutHandler.InboundHandler(() -> true, () -> 0, timeoutCounter, null));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");

        assertThrows(ClosedChannelException.class, () -> ch.writeInbound(request));

        verify(timeoutCounter).increment();
        assertNull(ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_TIMEOUT_FUTURE)
                .get());
        assertNull(
                ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_START_TIME).get());
    }

    @Test
    public void testNoTimeout() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpHeadersTimeoutHandler.InboundHandler(() -> true, () -> 10000, timeoutCounter, null));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");

        ch.writeInbound(request);

        verify(timeoutCounter, never()).increment();
        assertNull(ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_TIMEOUT_FUTURE)
                .get());
        assertNull(
                ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_START_TIME).get());
    }
}
