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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link SslHandshakeInfoHandler}.
 */
public class SslHandshakeInfoHandlerTest {

    @BeforeEach
    public void setup() {
        SslHandshakeInfoHandler.SNI_LOGGING_ENABLED = new DynamicBooleanProperty("zuul.ssl.handshake.snilogging.enabled", true);
    }

    @Test
    public void sslEarlyHandshakeFailure() throws Exception {
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
        assertThat(clientHello).isNotNull();
        ReferenceCountUtil.retain(clientHello);

        serverChannel.writeInbound(clientHello);

        // Assert that the handler removes itself from the pipeline, since it was torn down.
        assertThat(serverChannel.pipeline().context(SslHandshakeInfoHandler.class))
                .isNull();
    }

    @Test
    public void getFailureCauses() {
        SslHandshakeInfoHandler handler = new SslHandshakeInfoHandler();

        RuntimeException noMessage = new RuntimeException();
        assertThat(handler.getFailureCause(noMessage)).isEqualTo(noMessage.toString());

        RuntimeException withMessage = new RuntimeException("some unexpected message");
        assertThat(handler.getFailureCause(withMessage)).isEqualTo("some unexpected message");

        RuntimeException openSslMessage = new RuntimeException("javax.net.ssl.SSLHandshakeException: error:1000008e:SSL"
                + " routines:OPENSSL_internal:DIGEST_CHECK_FAILED");

        assertThat(handler.getFailureCause(openSslMessage)).isEqualTo("DIGEST_CHECK_FAILED");
    }

    @Test
    public void handshakeFailureWithSSLException() throws Exception {
        Registry registry = new DefaultRegistry();

        // Mock SSL engine and session
        SSLEngine sslEngine = mock(SSLEngine.class);
        ExtendedSSLSession sslSession = mock(ExtendedSSLSession.class);
        X509Certificate serverCert = mock(X509Certificate.class);

        // Setup SSL session
        when(sslEngine.getSession()).thenReturn(sslSession);
        when(sslEngine.getNeedClientAuth()).thenReturn(false);
        when(sslEngine.getWantClientAuth()).thenReturn(false);
        when(sslSession.getProtocol()).thenReturn("TLSv1.3");
        when(sslSession.getCipherSuite()).thenReturn("TLS_AES_256_GCM_SHA384");
        when(sslSession.getLocalCertificates()).thenReturn(new Certificate[]{serverCert});
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[0]);

        // Setup SNI with www.netflix.com
        List<SNIServerName> sniNames = Arrays.asList(new SNIHostName("www.netflix.com"));
        when(sslSession.getRequestedServerNames()).thenReturn(sniNames);

        // Create channel and context
        EmbeddedChannel channel = new EmbeddedChannel();
        CurrentPassport.fromChannel(channel);
        channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).set("192.168.1.1");

        SslHandshakeInfoHandler handler = new SslHandshakeInfoHandler(registry, false);

        SslHandler sslHandler = mock(SslHandler.class);
        when(sslHandler.engine()).thenReturn(sslEngine);
        channel.pipeline().addLast("ssl", sslHandler);
        channel.pipeline().addLast(handler);

        ChannelHandlerContext ctx = channel.pipeline().context(handler);

        // Create a failed handshake event
        SSLException sslException = new SSLException("Received fatal alert: certificate_unknown");
        SslHandshakeCompletionEvent failedEvent = new SslHandshakeCompletionEvent(sslException);

        // Trigger the event
        handler.userEventTriggered(ctx, failedEvent);

        // Verify success counter was incremented
        assertThat(registry.counter("server.ssl.handshake", "success", "false", "sni", "www.netflix.com", "failure_cause", "Received fatal alert: certificate_unknown").count()).isEqualTo(1);

        // Verify handler was removed from pipeline
        assertThat(channel.pipeline().context(SslHandshakeInfoHandler.class)).isNull();
    }

    @Test
    public void handshakeFailureWithClosedChannelException() throws Exception {
        // Setup mocks
        Registry registry = mock(Registry.class);
        Counter counter = mock(Counter.class);
        when(registry.counter(anyString())).thenReturn(counter);

        // Create handler with mocked registry
        SslHandshakeInfoHandler handler = new SslHandshakeInfoHandler(registry, false);

        // Create channel and context
        EmbeddedChannel channel = new EmbeddedChannel();
        CurrentPassport.fromChannel(channel);
        channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).set("192.168.1.1");
        channel.pipeline().addLast(handler);

        ChannelHandlerContext ctx = channel.pipeline().context(handler);

        // Create a failed handshake event with ClosedChannelException
        ClosedChannelException closedException = new ClosedChannelException();
        SslHandshakeCompletionEvent failedEvent = new SslHandshakeCompletionEvent(closedException);

        // Trigger the event
        handler.userEventTriggered(ctx, failedEvent);

        // Verify handler was removed from pipeline
        assertThat(channel.pipeline().context(SslHandshakeInfoHandler.class)).isNull();
    }

    @Test
    public void handshakeFailureWithHandshakeTimeout() throws Exception {
        // Setup mocks
        Registry registry = mock(Registry.class);
        Counter counter = mock(Counter.class);
        when(registry.counter(anyString())).thenReturn(counter);

        // Create handler with mocked registry
        SslHandshakeInfoHandler handler = new SslHandshakeInfoHandler(registry, false);

        // Create channel and context
        EmbeddedChannel channel = new EmbeddedChannel();
        CurrentPassport.fromChannel(channel);
        channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).set("192.168.1.1");
        channel.pipeline().addLast(handler);

        ChannelHandlerContext ctx = channel.pipeline().context(handler);

        // Create a failed handshake event with timeout
        SSLException timeoutException = new SSLException("handshake timed out");
        SslHandshakeCompletionEvent failedEvent = new SslHandshakeCompletionEvent(timeoutException);

        // Trigger the event
        handler.userEventTriggered(ctx, failedEvent);

        // Verify handler was removed from pipeline
        assertThat(channel.pipeline().context(SslHandshakeInfoHandler.class)).isNull();

        // Note: Counters should NOT be incremented for timeout as it's handled separately
    }

    @ParameterizedTest
    @ValueSource(strings = {"www.netflix.com", ""})
    public void handshakeSuccessWithSNI(String sni) throws Exception {
        Registry registry = new DefaultRegistry();

        // Create handler
        SslHandshakeInfoHandler handler = new SslHandshakeInfoHandler(registry, false);

        // Create channel
        EmbeddedChannel channel = new EmbeddedChannel();
        CurrentPassport.fromChannel(channel);

        // Mock SSL engine and session
        SSLEngine sslEngine = mock(SSLEngine.class);
        ExtendedSSLSession sslSession = mock(ExtendedSSLSession.class);
        X509Certificate serverCert = mock(X509Certificate.class);

        // Setup SSL session
        when(sslEngine.getSession()).thenReturn(sslSession);
        when(sslEngine.getNeedClientAuth()).thenReturn(false);
        when(sslEngine.getWantClientAuth()).thenReturn(false);
        when(sslSession.getProtocol()).thenReturn("TLSv1.3");
        when(sslSession.getCipherSuite()).thenReturn("TLS_AES_256_GCM_SHA384");
        when(sslSession.getLocalCertificates()).thenReturn(new Certificate[]{serverCert});
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[0]);

        if (!Objects.equals(sni, "")) {
            List<SNIServerName> sniNames = Arrays.asList(new SNIHostName("www.netflix.com"));
            when(sslSession.getRequestedServerNames()).thenReturn(sniNames);
        }

        // Add SSL handler and info handler to pipeline
        SslHandler sslHandler = mock(SslHandler.class);
        when(sslHandler.engine()).thenReturn(sslEngine);
        channel.pipeline().addLast("ssl", sslHandler);
        channel.pipeline().addLast(handler);

        ChannelHandlerContext ctx = channel.pipeline().context(handler);

        // Create successful handshake event
        SslHandshakeCompletionEvent successEvent = SslHandshakeCompletionEvent.SUCCESS;

        // Trigger the event
        handler.userEventTriggered(ctx, successEvent);

        String verifySni = Objects.equals(sni, "") ? "none" : sni;

        // Verify success counter was incremented
        assertThat(registry.counter("server.ssl.handshake", "success", "true", "sni", verifySni, "protocol", "TLSv1.3", "ciphersuite", "TLS_AES_256_GCM_SHA384", "clientauth", "NONE").count()).isEqualTo(1);

        // Verify SslHandshakeInfo was stored in channel attributes
        SslHandshakeInfo info = channel.attr(SslHandshakeInfoHandler.ATTR_SSL_INFO).get();
        assertThat(info).isNotNull();
        assertThat(info.getRequestedSni()).isEqualTo(verifySni);
        assertThat(info.getProtocol()).isEqualTo("TLSv1.3");
        assertThat(info.getCipherSuite()).isEqualTo("TLS_AES_256_GCM_SHA384");
        assertThat(info.getClientAuthRequirement()).isEqualTo(ClientAuth.NONE);
        assertThat(info.getClientCertificate()).isNull();

        // Verify passport state was updated
        assertThat(CurrentPassport.fromChannel(channel).getState())
                .isEqualTo(PassportState.SERVER_CH_SSL_HANDSHAKE_COMPLETE);

        // Verify handler was removed from pipeline
        assertThat(channel.pipeline().context(SslHandshakeInfoHandler.class)).isNull();
    }
}
