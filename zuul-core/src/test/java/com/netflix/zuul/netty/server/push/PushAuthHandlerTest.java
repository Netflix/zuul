/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.netty.server.push;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

class PushAuthHandlerTest {
    @Test
    void testIsInvalidOrigin() {
        ZuulPushAuthHandlerTest authHandler = new ZuulPushAuthHandlerTest();

        DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/ws", Unpooled.buffer());

        // Invalid input
        assertTrue(authHandler.isInvalidOrigin(request));
        request.headers().add(HttpHeaderNames.ORIGIN, "zuul-push.foo.com");
        assertTrue(authHandler.isInvalidOrigin(request));

        // Valid input
        request.headers().remove(HttpHeaderNames.ORIGIN);
        request.headers().add(HttpHeaderNames.ORIGIN, "zuul-push.netflix.com");
        assertFalse(authHandler.isInvalidOrigin(request));
    }

    class ZuulPushAuthHandlerTest extends PushAuthHandler {
        public ZuulPushAuthHandlerTest() {
            super("/ws", ".netflix.com");
        }

        @Override
        protected boolean isDelayedAuth(FullHttpRequest req, ChannelHandlerContext ctx) {
            return false;
        }

        @Override
        protected PushUserAuth doAuth(FullHttpRequest req, ChannelHandlerContext ctx) {
            return null;
        }
    }
}
