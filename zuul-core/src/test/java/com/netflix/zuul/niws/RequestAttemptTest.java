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