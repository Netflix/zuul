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
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Justin Guerra
 * @since 11/15/23
 */
class Http2ProtocolErrorHandlerTest {

    private EmbeddedChannel channel;
    private ExceptionCapturingHandler exceptionCapturingHandler;

    @BeforeEach
    void setup() {
        exceptionCapturingHandler = new ExceptionCapturingHandler();
        channel = new EmbeddedChannel(new Http2ProtocolErrorHandler(), exceptionCapturingHandler);
    }

    @Test
    void goAwayOnHardClose() {
        Http2Exception exception =
                new Http2Exception(Http2Error.PROTOCOL_ERROR, Http2Exception.ShutdownHint.HARD_SHUTDOWN);
        channel.pipeline().fireExceptionCaught(exception);
        Object msg = channel.readOutbound();
        assertTrue(msg instanceof Http2GoAwayFrame);
        Http2GoAwayFrame frame = (Http2GoAwayFrame) msg;
        assertEquals(Http2Error.PROTOCOL_ERROR.code(), frame.errorCode());
        assertFalse(channel.isActive());
    }

    @Test
    void passAlongGracefulClose() {
        Http2Exception exception =
                new Http2Exception(Http2Error.FLOW_CONTROL_ERROR, Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN);
        channel.pipeline().fireExceptionCaught(exception);
        assertEquals(exception, exceptionCapturingHandler.caught);
    }

    @Test
    public void otherExceptionsFiredAlong() {
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