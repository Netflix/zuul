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

package com.netflix.zuul.netty.server.psk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.DefaultAttributeMap;
import java.security.SecureRandom;
import java.util.Set;
import java.util.Vector;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZuulPskServerTest {

    private Registry registry;
    private ChannelHandlerContext ctx;
    private ExternalTlsPskProvider pskProvider;
    private ZuulPskServer server;
    private TlsCrypto crypto;

    @BeforeEach
    void setUp() {
        registry = new DefaultRegistry();
        ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.attr(ZuulPskServer.TLS_HANDSHAKE_USING_EXTERNAL_PSK))
                .thenReturn(new DefaultAttributeMap().attr(ZuulPskServer.TLS_HANDSHAKE_USING_EXTERNAL_PSK));
        pskProvider = mock(ExternalTlsPskProvider.class);
        crypto = new JcaTlsCryptoProvider().create(new SecureRandom());
        server = new ZuulPskServer(
                crypto, registry, pskProvider, ctx, Set.of(ProtocolName.HTTP_2_TLS, ProtocolName.HTTP_1_1));
    }

    @Test
    void credentialsAreNullForPskMode() {
        assertThat(server.getCredentials()).isNull();
    }

    @Test
    void supportedVersionsAreTls13Only() {
        ProtocolVersion[] versions = server.getSupportedVersions();

        assertThat(versions).containsExactly(ProtocolVersion.TLSv13);
    }

    @Test
    void supportedCipherSuitesMatchHandlerMap() {
        int[] suites = server.getSupportedCipherSuites();

        assertThat(suites).isNotEmpty();
        for (int suite : suites) {
            assertThat(TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP).containsKey(suite);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void protocolNamesContainConfiguredProtocols() {
        Vector<ProtocolName> names = server.getProtocolNames();

        assertThat(names).hasSize(2);
        assertThat(names).contains(ProtocolName.HTTP_2_TLS, ProtocolName.HTTP_1_1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void protocolNamesEmptyWhenNullConfigured() {
        ZuulPskServer serverNoProtocols = new ZuulPskServer(crypto, registry, pskProvider, ctx, null);

        Vector<ProtocolName> names = serverNoProtocols.getProtocolNames();

        assertThat(names).isEmpty();
    }

    @Test
    void cipherSuiteConstantsHaveExpectedValues() {
        assertThat(CipherSuite.TLS_AES_128_GCM_SHA256).isEqualTo(0x1301);
        assertThat(CipherSuite.TLS_AES_256_GCM_SHA384).isEqualTo(0x1302);
        assertThat(CipherSuite.TLS_CHACHA20_POLY1305_SHA256).isEqualTo(0x1303);
    }

    @Test
    void tlsCryptoProviderCreatesValidCrypto() {
        assertThat(crypto).isNotNull();
        assertThat(crypto.hasRSAEncryption()).isTrue();
    }
}
