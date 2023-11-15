/**
 * Copyright 2023 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.netty.server.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Justin Guerra
 * @since 11/15/23
 */
class Http2ConnectionErrorHandlerTest {

    private EmbeddedChannel channel;
    private ExceptionCapturingHandler exceptionCapturingHandler;

    @BeforeEach
    void setup() {
        exceptionCapturingHandler = new ExceptionCapturingHandler();
        channel = new EmbeddedChannel(new Http2ConnectionErrorHandler(), exceptionCapturingHandler);
    }

    @Test
    public void nonHttp2ExceptionsPassedUpPipeline() {
        RuntimeException exception = new RuntimeException();
        channel.pipeline().fireExceptionCaught(exception);
        assertEquals(exception, exceptionCapturingHandler.caught);
    }

    private static class ExceptionCapturingHandler extends ChannelInboundHandlerAdapter {

        private Throwable caught;

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            this.caught = cause;
        }
    }
}