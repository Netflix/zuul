package com.netflix.zuul.netty.server.push;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PushAuthHandlerTest {
    @Test
    public void testIsInvalidOrigin() {
        ZuulPushAuthHandlerTest authHandler = new ZuulPushAuthHandlerTest();

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/ws", Unpooled.buffer());

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
        protected PushUserAuth doAuth(FullHttpRequest req) {
            return null;
        }
    }
}