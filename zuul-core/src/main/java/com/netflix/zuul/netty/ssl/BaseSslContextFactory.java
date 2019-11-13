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

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslSessionStats;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import com.netflix.netty.common.ssl.ServerSslConfig;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

/**
 * User: michaels@netflix.com
 * Date: 3/4/16
 * Time: 4:00 PM
 */
public class BaseSslContextFactory implements SslContextFactory {
    private static final Logger LOG = LoggerFactory.getLogger(BaseSslContextFactory.class);

    private static final DynamicBooleanProperty ALLOW_USE_OPENSSL = new DynamicBooleanProperty("zuul.ssl.openssl.allow", true);

    static {
        // Install BouncyCastle provider.
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    protected final Registry spectatorRegistry;
    protected final ServerSslConfig serverSslConfig;

    public BaseSslContextFactory(Registry spectatorRegistry, ServerSslConfig serverSslConfig) {
        this.spectatorRegistry = spectatorRegistry;
        this.serverSslConfig = serverSslConfig;
    }

    @Override
    public SslContextBuilder createBuilderForServer() {
        try {
            ArrayList<X509Certificate> trustedCerts = getTrustedX509Certificates();
            SslProvider sslProvider = chooseSslProvider();

            LOG.warn("Using SslProvider of type - " + sslProvider.name() + " for certChainFile - " + serverSslConfig.getCertChainFile());

            InputStream certChainInput = new FileInputStream(serverSslConfig.getCertChainFile());
            InputStream keyInput = getKeyInputStream();

            SslContextBuilder builder = SslContextBuilder.forServer(certChainInput, keyInput)
                    .ciphers(getCiphers(), getCiphersFilter())
                    .sessionTimeout(serverSslConfig.getSessionTimeout())
                    .sslProvider(sslProvider);

            if (serverSslConfig.getClientAuth() != null && isNotEmpty(trustedCerts)) {
                builder = builder
                        .trustManager(trustedCerts.toArray(new X509Certificate[0]))
                        .clientAuth(serverSslConfig.getClientAuth());
            }

            return builder;
        }
        catch (Exception e) {
            throw new RuntimeException("Error configuring SslContext!", e);
        }
    }

    @Override
    public void enableSessionTickets(SslContext sslContext) {
        // TODO
    }

    public void configureOpenSslStatsMetrics(SslContext sslContext, String sslContextId) {
        // Setup metrics tracking the OpenSSL stats.
        if (sslContext instanceof ReferenceCountedOpenSslContext) {
            OpenSslSessionStats stats = ((ReferenceCountedOpenSslContext) sslContext).sessionContext().stats();

            openSslStatGauge(stats, sslContextId, "accept", OpenSslSessionStats::accept);
            openSslStatGauge(stats, sslContextId, "accept_good", OpenSslSessionStats::acceptGood);
            openSslStatGauge(stats, sslContextId, "accept_renegotiate", OpenSslSessionStats::acceptRenegotiate);
            openSslStatGauge(stats, sslContextId, "number", OpenSslSessionStats::number);
            openSslStatGauge(stats, sslContextId, "connect", OpenSslSessionStats::connect);
            openSslStatGauge(stats, sslContextId, "connect_good", OpenSslSessionStats::connectGood);
            openSslStatGauge(stats, sslContextId, "connect_renegotiate", OpenSslSessionStats::connectRenegotiate);
            openSslStatGauge(stats, sslContextId, "hits", OpenSslSessionStats::hits);
            openSslStatGauge(stats, sslContextId, "cb_hits", OpenSslSessionStats::cbHits);
            openSslStatGauge(stats, sslContextId, "misses", OpenSslSessionStats::misses);
            openSslStatGauge(stats, sslContextId, "timeouts", OpenSslSessionStats::timeouts);
            openSslStatGauge(stats, sslContextId, "cache_full", OpenSslSessionStats::cacheFull);
            openSslStatGauge(stats, sslContextId, "ticket_key_fail", OpenSslSessionStats::ticketKeyFail);
            openSslStatGauge(stats, sslContextId, "ticket_key_new", OpenSslSessionStats::ticketKeyNew);
            openSslStatGauge(stats, sslContextId, "ticket_key_renew", OpenSslSessionStats::ticketKeyRenew);
            openSslStatGauge(stats, sslContextId, "ticket_key_resume", OpenSslSessionStats::ticketKeyResume);
        }
    }

    private void openSslStatGauge(OpenSslSessionStats stats, String sslContextId, String statName, ToDoubleFunction<OpenSslSessionStats> value) {
        Id id = spectatorRegistry.createId("server.ssl.stats", "id", sslContextId, "stat", statName);
        spectatorRegistry.gauge(id, stats, value);
        LOG.debug("Registered spectator gauge - " + id.name());
    }

    public static SslProvider chooseSslProvider() {
        // Use openssl only if available and has ALPN support (ie. version > 1.0.2).
        SslProvider sslProvider;
        if (ALLOW_USE_OPENSSL.get() && OpenSsl.isAvailable() && OpenSsl.isAlpnSupported()) {
            sslProvider = SslProvider.OPENSSL;
        }
        else {
            sslProvider = SslProvider.JDK;
        }
        return sslProvider;
    }

    public ServerSslConfig getServerSslConfig() {
        return serverSslConfig;
    }

    @Override
    public String[] getProtocols() {
        return serverSslConfig.getProtocols();
    }

    public List<String> getCiphers() throws NoSuchAlgorithmException {
        return serverSslConfig.getCiphers();
    }

    protected CipherSuiteFilter getCiphersFilter() {
        return SupportedCipherSuiteFilter.INSTANCE;
    }

    protected ArrayList<X509Certificate> getTrustedX509Certificates() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        ArrayList<X509Certificate> trustedCerts = new ArrayList<>();

        // Add the certifcates from the JKS truststore - ie. the CA's of the client cert that peer Zuul's will use.
        if (serverSslConfig.getClientAuth() == ClientAuth.REQUIRE || serverSslConfig.getClientAuth() == ClientAuth.OPTIONAL) {
            // Get the encrypted bytes of the truststore password.
            byte[] trustStorePwdBytes;
            if (serverSslConfig.getClientAuthTrustStorePassword() != null) {
                trustStorePwdBytes = Base64.getDecoder().decode(serverSslConfig.getClientAuthTrustStorePassword());
            }
            else if (serverSslConfig.getClientAuthTrustStorePasswordFile() != null) {
                trustStorePwdBytes = FileUtils.readFileToByteArray(serverSslConfig.getClientAuthTrustStorePasswordFile());
            }
            else {
                throw new IllegalArgumentException("Must specify either ClientAuthTrustStorePassword or ClientAuthTrustStorePasswordFile!");
            }

            // Decrypt the truststore password.
            String trustStorePassword = getTruststorePassword(trustStorePwdBytes);

            boolean dumpDecryptedTrustStorePassword = false;
            if (dumpDecryptedTrustStorePassword) {
                LOG.debug("X509Cert Trust Store Password " + trustStorePassword);
            }

            final KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(new FileInputStream(serverSslConfig.getClientAuthTrustStoreFile()),
                    trustStorePassword.toCharArray());

            Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                X509Certificate cert = (X509Certificate) trustStore.getCertificate(aliases.nextElement());
                trustedCerts.add(cert);
            }
        }

        return trustedCerts;
    }

    /**
     * Can be overridden to implement your own decryption scheme.
     *
     * @param trustStorePwdBytes
     * @return
     */
    protected String getTruststorePassword(byte[] trustStorePwdBytes) {
        return new String(trustStorePwdBytes).trim();
    }

    /**
     * Can be overridden to implement your own decryption scheme.
     *
     * @return
     * @throws IOException
     */
    protected InputStream getKeyInputStream() throws IOException {
        return new FileInputStream(serverSslConfig.getKeyFile());
    }
}
