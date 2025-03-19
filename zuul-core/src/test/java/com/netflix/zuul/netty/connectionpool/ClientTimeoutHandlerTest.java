/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.zuul.netty.connectionpool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.util.ReferenceCountUtil;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Justin Guerra
 * @since 7/30/24
 */
@ExtendWith(MockitoExtension.class)
class ClientTimeoutHandlerTest {

    @Mock
    private PooledConnection pooledConnection;

    private EmbeddedChannel channel;
    private WriteVerifyingHandler verifier;

    @BeforeEach
    public void setup() {
        channel = new EmbeddedChannel();
        channel.attr(PooledConnection.CHANNEL_ATTR).set(pooledConnection);
        verifier = new WriteVerifyingHandler();
        channel.pipeline().addLast(verifier);
        channel.pipeline().addLast(new ClientTimeoutHandler.OutboundHandler());
    }

    @AfterEach
    public void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    public void dontStartReadTimeoutHandlerIfNotLastContent() {
        addTimeoutToChannel();
        channel.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer("yo".getBytes(UTF_8))));
        verify(pooledConnection, never()).startReadTimeoutHandler(any());
        verifyWrite();
    }

    @Test
    public void dontStartReadTimeoutHandlerIfNoTimeout() {
        channel.writeOutbound(new DefaultLastHttpContent());
        verify(pooledConnection, never()).startReadTimeoutHandler(any());
        verifyWrite();
    }

    @Test
    public void dontStartReadTimeoutHandlerOnFailedPromise() {
        addTimeoutToChannel();

        channel.pipeline().addFirst(new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.safeRelease(msg);
                promise.setFailure(new RuntimeException());
            }
        });
        try {
            channel.writeOutbound(new DefaultLastHttpContent());
        } catch (RuntimeException e) {
            // expected
        }
        verify(pooledConnection, never()).startReadTimeoutHandler(any());
        verifyWrite();
    }

    @Test
    public void startReadTimeoutHandlerOnSuccessfulPromise() {
        Duration timeout = addTimeoutToChannel();
        channel.writeOutbound(new DefaultLastHttpContent());
        verify(pooledConnection).startReadTimeoutHandler(timeout);
        verifyWrite();
    }

    private Duration addTimeoutToChannel() {
        Duration timeout = Duration.of(5, ChronoUnit.SECONDS);
        channel.attr(ClientTimeoutHandler.ORIGIN_RESPONSE_READ_TIMEOUT).set(timeout);
        return timeout;
    }

    private void verifyWrite() {
        Assertions.assertTrue(verifier.seenWrite);
    }

    private static class WriteVerifyingHandler extends ChannelDuplexHandler {
        boolean seenWrite;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            seenWrite = true;
            super.write(ctx, msg, promise);
        }
    }
}
