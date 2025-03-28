/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.netty.server.ssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import java.nio.channels.ClosedChannelException;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SslHandshakeInfoHandler}.
 */
class SslHandshakeInfoHandlerTest {

    @Test
    void sslEarlyHandshakeFailure() throws Exception {
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        SSLEngine clientEngine = SslContextBuilder.forClient().build().newEngine(clientChannel.alloc());
        clientChannel.pipeline().addLast(new SslHandler(clientEngine));

        EmbeddedChannel serverChannel = new EmbeddedChannel();
        SelfSignedCertificate cert = new SelfSignedCertificate("localhorse");
        SSLEngine serverEngine =
                SslContextBuilder.forServer(cert.key(), cert.cert()).build().newEngine(serverChannel.alloc());

        serverChannel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                // Simulate an early closure form the client.
                ReferenceCountUtil.safeRelease(msg);
                promise.setFailure(new ClosedChannelException());
            }
        });
        serverChannel.pipeline().addLast(new SslHandler(serverEngine));
        serverChannel.pipeline().addLast(new SslHandshakeInfoHandler());

        Object clientHello = clientChannel.readOutbound();
        assertNotNull(clientHello);
        ReferenceCountUtil.retain(clientHello);

        serverChannel.writeInbound(clientHello);

        // Assert that the handler removes itself from the pipeline, since it was torn down.
        assertNull(serverChannel.pipeline().context(SslHandshakeInfoHandler.class));
    }

    @Test
    void getFailureCauses() {
        SslHandshakeInfoHandler handler = new SslHandshakeInfoHandler();

        RuntimeException noMessage = new RuntimeException();
        assertEquals(noMessage.toString(), handler.getFailureCause(noMessage));

        RuntimeException withMessage = new RuntimeException("some unexpected message");
        assertEquals("some unexpected message", handler.getFailureCause(withMessage));

        RuntimeException openSslMessage = new RuntimeException("javax.net.ssl.SSLHandshakeException: error:1000008e:SSL"
                + " routines:OPENSSL_internal:DIGEST_CHECK_FAILED");

        assertEquals("DIGEST_CHECK_FAILED", handler.getFailureCause(openSslMessage));
    }
}
