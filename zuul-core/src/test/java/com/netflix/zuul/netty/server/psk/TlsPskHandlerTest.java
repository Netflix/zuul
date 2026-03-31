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

import com.netflix.spectator.api.DefaultRegistry;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ProtocolName;
import org.junit.jupiter.api.Test;

class TlsPskHandlerTest {

    @Test
    void cipherSuiteMapContainsAes128Gcm() {
        assertThat(TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP)
                .containsKey(CipherSuite.TLS_AES_128_GCM_SHA256);
        assertThat(TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP.get(CipherSuite.TLS_AES_128_GCM_SHA256))
                .isEqualTo("TLS_AES_128_GCM_SHA256");
    }

    @Test
    void cipherSuiteMapContainsAes256Gcm() {
        assertThat(TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP)
                .containsKey(CipherSuite.TLS_AES_256_GCM_SHA384);
        assertThat(TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP.get(CipherSuite.TLS_AES_256_GCM_SHA384))
                .isEqualTo("TLS_AES_256_GCM_SHA384");
    }

    @Test
    void cipherSuiteMapHasExactlyTwoEntries() {
        assertThat(TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP).hasSize(2);
    }

    @Test
    void handlerAddedInsertsTlsPskDecoder() {
        ExternalTlsPskProvider pskProvider = mock(ExternalTlsPskProvider.class);
        TlsPskHandler handler = new TlsPskHandler(
                new DefaultRegistry(),
                pskProvider,
                Set.of(ProtocolName.HTTP_2_TLS));

        EmbeddedChannel channel = new EmbeddedChannel(handler);
        ChannelPipeline pipeline = channel.pipeline();

        assertThat(pipeline.get("tls_psk_handler")).isNotNull();
        assertThat(pipeline.get("tls_psk_handler")).isInstanceOf(TlsPskDecoder.class);
        channel.close();
    }

    @Test
    void applicationProtocolNullBeforeHandshake() {
        ExternalTlsPskProvider pskProvider = mock(ExternalTlsPskProvider.class);
        TlsPskHandler handler = new TlsPskHandler(
                new DefaultRegistry(),
                pskProvider,
                Set.of(ProtocolName.HTTP_2_TLS));

        assertThat(handler.getApplicationProtocol()).isNull();
    }

    @Test
    void sessionAvailableBeforeHandshake() {
        ExternalTlsPskProvider pskProvider = mock(ExternalTlsPskProvider.class);
        TlsPskHandler handler = new TlsPskHandler(
                new DefaultRegistry(),
                pskProvider,
                Set.of(ProtocolName.HTTP_1_1));

        assertThat(handler.getSession()).isNotNull();
    }
}
