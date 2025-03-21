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

import com.google.common.primitives.Bytes;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.bouncycastle.tls.AbstractTlsServer;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.AlertLevel;
import org.bouncycastle.tls.BasicTlsPSKExternal;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.PRFAlgorithm;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.PskIdentity;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsPSKExternal;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZuulPskServer extends AbstractTlsServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZuulPskServer.class);

    public static final AttributeKey<Boolean> TLS_HANDSHAKE_USING_EXTERNAL_PSK =
            AttributeKey.newInstance("_tls_handshake_using_external_psk");

    private static class PSKTimings {
        private final Timer handshakeCompleteTimer;

        private Long handshakeStartTime;

        PSKTimings(Registry registry) {
            handshakeCompleteTimer = registry.timer("zuul.psk.handshake.complete.time");
        }

        public void recordHandshakeStarting() {
            handshakeStartTime = System.nanoTime();
        }

        public void recordHandshakeComplete() {
            handshakeCompleteTimer.record(System.nanoTime() - handshakeStartTime, TimeUnit.NANOSECONDS);
        }
    }

    private final PSKTimings pskTimings;

    private final ExternalTlsPskProvider externalTlsPskProvider;

    private final ChannelHandlerContext ctx;

    private final Set<ProtocolName> supportedApplicationProtocols;

    public ZuulPskServer(
            TlsCrypto crypto,
            Registry registry,
            ExternalTlsPskProvider externalTlsPskProvider,
            ChannelHandlerContext ctx,
            Set<ProtocolName> supportedApplicationProtocols) {
        super(crypto);
        this.pskTimings = new PSKTimings(registry);
        this.externalTlsPskProvider = externalTlsPskProvider;
        this.ctx = ctx;
        this.supportedApplicationProtocols = supportedApplicationProtocols;
    }

    @Override
    public TlsCredentials getCredentials() {
        return null;
    }

    @Override
    protected Vector getProtocolNames() {
        Vector protocolNames = new Vector();
        if (supportedApplicationProtocols != null) {
            supportedApplicationProtocols.forEach(protocolNames::addElement);
        }
        return protocolNames;
    }

    @Override
    public void notifyHandshakeBeginning() throws IOException {
        pskTimings.recordHandshakeStarting();
        this.ctx.channel().attr(TLS_HANDSHAKE_USING_EXTERNAL_PSK).set(false);
        // TODO: sunnys - handshake timeouts
        super.notifyHandshakeBeginning();
    }

    @Override
    public void notifyHandshakeComplete() throws IOException {
        pskTimings.recordHandshakeComplete();
        this.ctx.channel().attr(TLS_HANDSHAKE_USING_EXTERNAL_PSK).set(true);
        super.notifyHandshakeComplete();
        ctx.fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
    }

    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return ProtocolVersion.TLSv13.only();
    }

    @Override
    protected int[] getSupportedCipherSuites() {
        return TlsUtils.getSupportedCipherSuites(
                getCrypto(),
                TlsPskHandler.SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP.keySet().stream()
                        .mapToInt(Number::intValue)
                        .toArray());
    }

    @Override
    public ProtocolVersion getServerVersion() throws IOException {
        return super.getServerVersion();
    }

    /**
     * TODO: Ask BC folks to see if getExternalPSK can throw a checked exception
     * https://github.com/bcgit/bc-java/issues/1673
     * We are using SneakyThrows here because getExternalPSK is an override and we cant have throws in the method signature
     * and we dont want to catch and wrap in RuntimeException.
     * SneakyThrows allows up to compile and it will throw the exception at runtime.
     */
    @Override
    @SneakyThrows
    public TlsPSKExternal getExternalPSK(Vector clientPskIdentities) {
        byte[] clientPskIdentity = ((PskIdentity) clientPskIdentities.get(0)).getIdentity();
        byte[] psk;
        try {
            this.ctx
                    .channel()
                    .attr(TlsPskHandler.CLIENT_PSK_IDENTITY_ATTRIBUTE_KEY)
                    .set(new ClientPSKIdentityInfo(List.copyOf(Bytes.asList(clientPskIdentity))));
            psk = externalTlsPskProvider.provide(
                    clientPskIdentity,
                    this.context.getSecurityParametersHandshake().getClientRandom());
        } catch (PskCreationFailureException e) {
            throw switch (e.getTlsAlertMessage()) {
                case unknown_psk_identity -> new TlsFatalAlert(
                        AlertDescription.unknown_psk_identity, "Unknown or null client PSk identity");
                case decrypt_error -> new TlsFatalAlert(
                        AlertDescription.decrypt_error, "Invalid or expired client PSk identity");
            };
        }
        TlsSecret pskTlsSecret = getCrypto().createSecret(psk);
        int prfAlgorithm = getPRFAlgorithm13(getSelectedCipherSuite());
        return new BasicTlsPSKExternal(clientPskIdentity, pskTlsSecret, prfAlgorithm);
    }

    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        super.notifyAlertRaised(alertLevel, alertDescription, message, cause);
        Consumer<String> loggerFunc = (alertLevel == AlertLevel.fatal) ? LOGGER::error : LOGGER::debug;
        loggerFunc.accept("TLS/PSK server raised alert: " + AlertLevel.getText(alertLevel) + ", "
                + AlertDescription.getText(alertDescription));
        if (message != null) {
            loggerFunc.accept("> " + message);
        }
        if (cause != null) {
            LOGGER.error("TLS/PSK alert stacktrace", cause);
        }

        if (alertDescription == AlertDescription.close_notify) {
            ctx.fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS);
        }
    }

    @Override
    public void notifyAlertReceived(short alertLevel, short alertDescription) {
        Consumer<String> loggerFunc = (alertLevel == AlertLevel.fatal) ? LOGGER::error : LOGGER::debug;
        loggerFunc.accept("TLS 1.3 PSK server received alert: " + AlertLevel.getText(alertLevel) + ", "
                + AlertDescription.getText(alertDescription));
    }

    @Override
    public void processClientExtensions(Hashtable clientExtensions) throws IOException {
        if (context.getSecurityParametersHandshake().getClientRandom() == null) {
            throw new TlsFatalAlert(AlertDescription.internal_error);
        }
        super.processClientExtensions(clientExtensions);
    }

    @Override
    public Hashtable getServerExtensions() throws IOException {
        if (context.getSecurityParametersHandshake().getServerRandom() == null) {
            throw new TlsFatalAlert(AlertDescription.internal_error);
        }
        return super.getServerExtensions();
    }

    @Override
    public void getServerExtensionsForConnection(Hashtable serverExtensions) throws IOException {
        if (context.getSecurityParametersHandshake().getServerRandom() == null) {
            throw new TlsFatalAlert(AlertDescription.internal_error);
        }
        super.getServerExtensionsForConnection(serverExtensions);
    }

    public String getApplicationProtocol() {
        ProtocolName protocolName = context.getSecurityParametersConnection().getApplicationProtocol();
        if (protocolName != null) {
            return protocolName.getUtf8Decoding();
        }
        return null;
    }

    private static int getPRFAlgorithm13(int cipherSuite) {
        return switch (cipherSuite) {
            case CipherSuite.TLS_AES_128_CCM_SHA256,
                    CipherSuite.TLS_AES_128_CCM_8_SHA256,
                    CipherSuite.TLS_AES_128_GCM_SHA256,
                    CipherSuite.TLS_CHACHA20_POLY1305_SHA256 -> PRFAlgorithm.tls13_hkdf_sha256;
            case CipherSuite.TLS_AES_256_GCM_SHA384 -> PRFAlgorithm.tls13_hkdf_sha384;
            case CipherSuite.TLS_SM4_CCM_SM3, CipherSuite.TLS_SM4_GCM_SM3 -> PRFAlgorithm.tls13_hkdf_sm3;
            default -> -1;
        };
    }
}
