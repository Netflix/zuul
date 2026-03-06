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

package com.netflix.netty.common.ssl;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.ssl.ClientAuth;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerSslConfigTest {

    @Test
    void builderSetsDefaults() {
        ServerSslConfig config = ServerSslConfig.builder().build();

        assertThat(config.getClientAuth()).isEqualTo(ClientAuth.NONE);
        assertThat(config.getSessionTimeout()).isGreaterThan(0);
        assertThat(config.isSessionTicketsEnabled()).isFalse();
        assertThat(config.getProtocols()).isNull();
        assertThat(config.getCiphers()).isNull();
        assertThat(config.getCertChainFile()).isNull();
        assertThat(config.getKeyFile()).isNull();
        assertThat(config.getClientAuthTrustStoreFile()).isNull();
        assertThat(config.getClientAuthTrustStorePassword()).isNull();
        assertThat(config.getClientAuthTrustStorePasswordFile()).isNull();
    }

    @Test
    void builderSetsAllFields() {
        File certFile = new File("cert.pem");
        File keyFile = new File("key.pem");
        File trustStoreFile = new File("truststore.jks");
        List<String> ciphers = List.of("TLS_AES_128_GCM_SHA256");

        ServerSslConfig config = ServerSslConfig.builder()
                .protocols(new String[] {"TLSv1.3", "TLSv1.2"})
                .ciphers(ciphers)
                .certChainFile(certFile)
                .keyFile(keyFile)
                .clientAuth(ClientAuth.REQUIRE)
                .clientAuthTrustStoreFile(trustStoreFile)
                .clientAuthTrustStorePassword("secret")
                .sessionTimeout(3600)
                .sessionTicketsEnabled(true)
                .build();

        assertThat(config.getProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(config.getCiphers()).containsExactly("TLS_AES_128_GCM_SHA256");
        assertThat(config.getCertChainFile()).isEqualTo(certFile);
        assertThat(config.getKeyFile()).isEqualTo(keyFile);
        assertThat(config.getClientAuth()).isEqualTo(ClientAuth.REQUIRE);
        assertThat(config.getClientAuthTrustStoreFile()).isEqualTo(trustStoreFile);
        assertThat(config.getClientAuthTrustStorePassword()).isEqualTo("secret");
        assertThat(config.getSessionTimeout()).isEqualTo(3600);
        assertThat(config.isSessionTicketsEnabled()).isTrue();
    }

    @Test
    void getDefaultCiphersReturnsNonEmptyList() {
        assertThat(ServerSslConfig.getDefaultCiphers()).isNotEmpty();
    }
}
