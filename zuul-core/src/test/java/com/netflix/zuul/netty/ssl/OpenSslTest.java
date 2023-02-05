package com.netflix.zuul.netty.ssl;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenSslTest {
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
