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

import com.netflix.config.DynamicLongProperty;
import io.netty.handler.ssl.ClientAuth;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Server-side SSL/TLS configuration including protocols, ciphers, certificate
 * material, client authentication, and session settings.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class ServerSslConfig {
    private static final DynamicLongProperty DEFAULT_SESSION_TIMEOUT =
            new DynamicLongProperty("server.ssl.session.timeout", (18 * 60)); // 18 hours

    private static final List<String> DEFAULT_CIPHERS;

    static {
        try {
            SSLContext context = SSLContext.getDefault();
            SSLSocketFactory sf = context.getSocketFactory();
            DEFAULT_CIPHERS = List.of(sf.getSupportedCipherSuites());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final String[] protocols;
    private final List<String> ciphers;
    private final File certChainFile;
    private final File keyFile;

    @Builder.Default
    private final ClientAuth clientAuth = ClientAuth.NONE;

    private final File clientAuthTrustStoreFile;
    private final String clientAuthTrustStorePassword;
    private final File clientAuthTrustStorePasswordFile;

    @Builder.Default
    private final long sessionTimeout = DEFAULT_SESSION_TIMEOUT.get();

    @Builder.Default
    private final boolean sessionTicketsEnabled = false;

    /**
     * @deprecated Use {@link ServerSslConfig#builder()} instead.
     */
    @Deprecated
    public ServerSslConfig(String[] protocols, String[] ciphers, File certChainFile, File keyFile) {
        this(protocols, ciphers, certChainFile, keyFile, ClientAuth.NONE, null, (File) null, false);
    }

    /**
     * @deprecated Use {@link ServerSslConfig#builder()} instead.
     */
    @Deprecated
    public ServerSslConfig(
            String[] protocols, String[] ciphers, File certChainFile, File keyFile, ClientAuth clientAuth) {
        this(protocols, ciphers, certChainFile, keyFile, clientAuth, null, (File) null, true);
    }

    /**
     * @deprecated Use {@link ServerSslConfig#builder()} instead.
     */
    @Deprecated
    public ServerSslConfig(
            String[] protocols,
            String[] ciphers,
            File certChainFile,
            File keyFile,
            ClientAuth clientAuth,
            File clientAuthTrustStoreFile,
            File clientAuthTrustStorePasswordFile,
            boolean sessionTicketsEnabled) {
        this.protocols = protocols;
        this.ciphers = ciphers != null ? Arrays.asList(ciphers) : null;
        this.certChainFile = certChainFile;
        this.keyFile = keyFile;
        this.clientAuth = clientAuth;
        this.clientAuthTrustStoreFile = clientAuthTrustStoreFile;
        this.clientAuthTrustStorePassword = null;
        this.clientAuthTrustStorePasswordFile = clientAuthTrustStorePasswordFile;
        this.sessionTimeout = DEFAULT_SESSION_TIMEOUT.get();
        this.sessionTicketsEnabled = sessionTicketsEnabled;
    }

    /**
     * @deprecated Use {@link ServerSslConfig#builder()} instead.
     */
    @Deprecated
    public ServerSslConfig(
            String[] protocols,
            String[] ciphers,
            File certChainFile,
            File keyFile,
            ClientAuth clientAuth,
            File clientAuthTrustStoreFile,
            String clientAuthTrustStorePassword,
            boolean sessionTicketsEnabled) {
        this.protocols = protocols;
        this.ciphers = Arrays.asList(ciphers);
        this.certChainFile = certChainFile;
        this.keyFile = keyFile;
        this.clientAuth = clientAuth;
        this.clientAuthTrustStoreFile = clientAuthTrustStoreFile;
        this.clientAuthTrustStorePassword = clientAuthTrustStorePassword;
        this.clientAuthTrustStorePasswordFile = null;
        this.sessionTimeout = DEFAULT_SESSION_TIMEOUT.get();
        this.sessionTicketsEnabled = sessionTicketsEnabled;
    }

    public static List<String> getDefaultCiphers() {
        return DEFAULT_CIPHERS;
    }

    /**
     * @deprecated Use {@link ServerSslConfig#builder()} instead.
     */
    @Deprecated
    public static ServerSslConfig withDefaultCiphers(File certChainFile, File keyFile, String... protocols) {
        return ServerSslConfig.builder()
                .protocols(protocols)
                .ciphers(getDefaultCiphers())
                .certChainFile(certChainFile)
                .keyFile(keyFile)
                .build();
    }

    @Override
    public String toString() {
        return "ServerSslConfig{" + "protocols="
                + Arrays.toString(protocols) + ", ciphers="
                + ciphers + ", certChainFile="
                + certChainFile + ", keyFile="
                + keyFile + ", clientAuth="
                + clientAuth + ", clientAuthTrustStoreFile="
                + clientAuthTrustStoreFile + ", sessionTimeout="
                + sessionTimeout + ", sessionTicketsEnabled="
                + sessionTicketsEnabled + '}';
    }
}
