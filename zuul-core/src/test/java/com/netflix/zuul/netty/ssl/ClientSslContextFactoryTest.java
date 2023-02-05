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

package com.netflix.zuul.netty.ssl;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import com.netflix.spectator.api.DefaultRegistry;
import io.netty.handler.ssl.OpenSslClientContext;
import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSessionContext;


/**
 * Tests for {@link ClientSslContextFactory}.
 */
class ClientSslContextFactoryTest {

    @Test
    void enableTls13() {
        String[] protos = ClientSslContextFactory.maybeAddTls13(true, "TLSv1.2");

        assertEquals(Arrays.asList("TLSv1.3", "TLSv1.2"), Arrays.asList(protos));
    }

    @Test
    void disableTls13() {
        String[] protos = ClientSslContextFactory.maybeAddTls13(false, "TLSv1.2");

        assertEquals(Arrays.asList("TLSv1.2"), Arrays.asList(protos));
    }

    @Test
    void testGetSslContext() {
        ClientSslContextFactory factory = new ClientSslContextFactory(new DefaultRegistry());
        SslContext sslContext = factory.getClientSslContext();
        assertThat(sslContext).isInstanceOf(OpenSslClientContext.class);
        assertThat(sslContext.isClient()).isTrue();
        assertThat(sslContext.isServer()).isFalse();
        SSLSessionContext sessionContext = sslContext.sessionContext();
        assertThat(sessionContext.getSessionCacheSize()).isEqualTo(20480);
        assertThat(sessionContext.getSessionTimeout()).isEqualTo(300);
    }

    @Test
    void testGetProtocols() {
        ClientSslContextFactory factory = new ClientSslContextFactory(new DefaultRegistry());
        assertThat(factory.getProtocols()).isEqualTo(new String[] { "TLSv1.2" });
    }

    @Test
    void testGetCiphers() throws Exception {
        ClientSslContextFactory factory = new ClientSslContextFactory(new DefaultRegistry());
        List<String> ciphers = factory.getCiphers();;
        assertThat(ciphers).containsNoDuplicates();
        assertThat(ciphers).containsExactly(
                "TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256", "TLS_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384", "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_256_CBC_SHA256", "TLS_RSA_WITH_AES_128_CBC_SHA256", "TLS_RSA_WITH_AES_256_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
    }
}
