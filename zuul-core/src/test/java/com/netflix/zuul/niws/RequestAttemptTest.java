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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import java.io.IOException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

public class RequestAttemptTest {

    @Test
    void exceptionHandled() {

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new RuntimeException("runtime failure"));

        assertThat(attempt.getError()).isEqualTo("runtime failure");
    }

    @Test
    void originConnectExceptionUnwrapped() {

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new OriginConnectException(
                "origin connect failure",
                new SSLHandshakeException("Invalid tls cert"),
                OutboundErrorType.CONNECT_ERROR));

        assertThat(attempt.getError()).isEqualTo("ORIGIN_CONNECT_ERROR");
        assertThat(attempt.getCause()).isEqualTo("Invalid tls cert");
    }

    @Test
    void originConnectExceptionWithSSLHandshakeCauseUnwrapped() {

        SSLHandshakeException handshakeException = mock(SSLHandshakeException.class);
        when(handshakeException.getCause()).thenReturn(new CertificateException("Cert doesn't match expected"));

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new OriginConnectException(
                "origin connect failure", handshakeException, OutboundErrorType.CONNECT_ERROR));

        assertThat(attempt.getError()).isEqualTo("ORIGIN_CONNECT_ERROR");
        assertThat(attempt.getCause()).isEqualTo("Cert doesn't match expected");
    }

    @Test
    void originConnectExceptionWithCauseNotUnwrapped() {
        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(new OriginConnectException(
                "origin connect failure",
                new IOException(new RuntimeException("socket failure")),
                OutboundErrorType.CONNECT_ERROR));

        assertThat(attempt.getError()).isEqualTo("ORIGIN_CONNECT_ERROR");
        assertThat(attempt.getCause()).isEqualTo("java.lang.RuntimeException: socket failure");
    }

    @Test
    void h2ExceptionCauseHandled() {
        // mock out a real-ish h2 stream exception
        Exception h2Exception = spy(Http2Exception.streamError(
                100,
                Http2Error.REFUSED_STREAM,
                "Cannot create stream 100 greater than Last-Stream-ID 99 from GOAWAY.",
                new Object[] {100, 99}));

        // mock a stacktrace to ensure we don't actually capture it completely
        when(h2Exception.getStackTrace()).thenReturn(new StackTraceElement[] {
            new StackTraceElement(
                    DefaultHttp2Connection.class.getCanonicalName(),
                    "createStream",
                    "DefaultHttp2Connection.java",
                    772),
            new StackTraceElement(
                    DefaultHttp2Connection.class.getCanonicalName(),
                    "checkNewStreamAllowed",
                    "DefaultHttp2Connection.java",
                    902)
        });

        RequestAttempt attempt = new RequestAttempt(1, null, null, "target", "chosen", 200, null, null, 0, 0, 0);
        attempt.setException(h2Exception);

        assertThat(attempt.getError())
                .isEqualTo("Cannot create stream 100 greater than Last-Stream-ID 99 from GOAWAY.");
        assertThat(attempt.getExceptionType()).isEqualTo("StreamException");

        assertThat(attempt.getCause())
                .isEqualTo(
                        "io.netty.handler.codec.http2.DefaultHttp2Connection.createStream(DefaultHttp2Connection.java:772)");
    }
}
