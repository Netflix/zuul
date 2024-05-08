/*
 * Copyright 2023 Netflix, Inc.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenSslTest {
    @BeforeEach
    void beforeEach() {
        OpenSsl.ensureAvailability();
        assertTrue(OpenSsl.isAvailable());
    }

    @Test
    void testBoringSsl() {
        assertThat(OpenSsl.versionString()).isEqualTo("BoringSSL");
        assertTrue(SslProvider.isAlpnSupported(SslProvider.OPENSSL));
        assertTrue(SslProvider.isTlsv13Supported(SslProvider.OPENSSL));
    }
}
