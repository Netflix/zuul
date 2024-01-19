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

package com.netflix.zuul.niws;

import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.security.cert.CertificateException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestAttemptTest {

    @Test
    void exceptionHandled() {

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new RuntimeException("runtime failure"));

        assertEquals("runtime failure", attempt.getError());
    }

    @Test
    void originConnectExceptionUnwrapped() {

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new OriginConnectException(
                "origin connect failure",
                new SSLHandshakeException("Invalid tls cert"),
                OutboundErrorType.CONNECT_ERROR));

        assertEquals("ORIGIN_CONNECT_ERROR", attempt.getError());
        assertEquals("Invalid tls cert", attempt.getCause());
    }

    @Test
    void originConnectExceptionWithSSLHandshakeCauseUnwrapped() {

        SSLHandshakeException handshakeException = mock(SSLHandshakeException.class);
        when(handshakeException.getCause()).thenReturn(new CertificateException("Cert doesn't match expected"));

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new OriginConnectException(
                "origin connect failure", handshakeException, OutboundErrorType.CONNECT_ERROR));

        assertEquals("ORIGIN_CONNECT_ERROR", attempt.getError());
        assertEquals("Cert doesn't match expected", attempt.getCause());
    }

    @Test
    void originConnectExceptionWithCauseNotUnwrapped() {
        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new OriginConnectException(
                "origin connect failure",
                new IOException(new RuntimeException("socket failure")),
                OutboundErrorType.CONNECT_ERROR));

        assertEquals("ORIGIN_CONNECT_ERROR", attempt.getError());
        assertEquals("java.lang.RuntimeException: socket failure", attempt.getCause());
    }
}