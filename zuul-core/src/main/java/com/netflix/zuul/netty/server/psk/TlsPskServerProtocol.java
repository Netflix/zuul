package com.netflix.zuul.netty.server.psk;

import org.bouncycastle.tls.TlsServerProtocol;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;

public class TlsPskServerProtocol extends TlsServerProtocol {

    public SSLSession getSSLSession() {
        return new SSLSession() {
            @Override
            public byte[] getId() {
                return tlsSession.getSessionID();
            }

            @Override
            public SSLSessionContext getSessionContext() {
                return null;
            }

            @Override
            public long getCreationTime() {
                return 0;
            }

            @Override
            public long getLastAccessedTime() {
                return 0;
            }

            @Override
            public void invalidate() {}

            @Override
            public boolean isValid() {
                return !isClosed();
            }

            @Override
            public void putValue(String name, Object value) {}

            @Override
            public Object getValue(String name) {
                return null;
            }

            @Override
            public void removeValue(String name) {}

            @Override
            public String[] getValueNames() {
                return new String[0];
            }

            @Override
            public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                return new Certificate[0];
            }

            @Override
            public Certificate[] getLocalCertificates() {
                return new Certificate[0];
            }

            @Override
            public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
                return new X509Certificate[0];
            }

            @Override
            public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                return null;
            }

            @Override
            public Principal getLocalPrincipal() {
                return null;
            }

            @Override
            public String getCipherSuite() {
                return TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP.get(
                        getContext().getSecurityParameters().getCipherSuite());
            }

            @Override
            public String getProtocol() {
                return getContext().getServerVersion().getName();
            }

            @Override
            public String getPeerHost() {
                return null;
            }

            @Override
            public int getPeerPort() {
                return 0;
            }

            @Override
            public int getPacketBufferSize() {
                return 0;
            }

            @Override
            public int getApplicationBufferSize() {
                return 0;
            }
        };
    }
}
