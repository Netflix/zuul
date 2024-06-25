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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
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
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsSecret;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TlsPskHandler extends ByteToMessageDecoder
        implements ChannelOutboundHandler, ChannelInboundHandler {

    public static final Map<Integer, String> SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP = Map.of(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            "TLS_AES_128_GCM_SHA256",
            CipherSuite.TLS_AES_256_GCM_SHA384,
            "TLS_AES_256_GCM_SHA384");

    public static final AttributeKey<ClientPSKIdentityInfo> CLIENT_PSK_IDENTITY_ATTRIBUTE_KEY =
            AttributeKey.newInstance("_client_psk_identity_info");

    public static final AttributeKey<Boolean> TLS_HANDSHAKE_USING_EXTERNAL_PSK =
            AttributeKey.newInstance("_tls_handshake_using_external_psk");

    private final Registry registry;
    private final ExternalTlsPskProvider externalTlsPskProvider;

    private ZuulPskServer tlsPskServer;

    private TlsPskServerProtocol tlsPskServerProtocol;

    public TlsPskHandler(Registry registry, ExternalTlsPskProvider externalTlsPskProvider) {
        super();
        this.registry = registry;
        this.externalTlsPskProvider = externalTlsPskProvider;
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise)
            throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof ByteBuf byteBufMsg)) {
            ctx.write(msg, promise);
            return;
        }
        byte[] appDataBytes = byteBufMsg.hasArray() ? byteBufMsg.array() : readDirect(byteBufMsg);
        ReferenceCountUtil.safeRelease(byteBufMsg);
        tlsPskServerProtocol.writeApplicationData(appDataBytes, 0, appDataBytes.length);
        int availableOutputBytes = tlsPskServerProtocol.getAvailableOutputBytes();
        if (availableOutputBytes != 0) {
            byte[] outputBytes = new byte[availableOutputBytes];
            tlsPskServerProtocol.readOutput(outputBytes, 0, availableOutputBytes);
            ctx.write(Unpooled.wrappedBuffer(outputBytes), promise)
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        final byte[] bytesRead = in.hasArray() ? in.array() : readDirect(in);
        try {
            tlsPskServerProtocol.offerInput(bytesRead);
        } catch (TlsFatalAlert tlsFatalAlert) {
            writeOutputIfAvailable(ctx);
            return;
        }
        writeOutputIfAvailable(ctx);
        final int appDataAvailable = tlsPskServerProtocol.getAvailableInputBytes();
        if (appDataAvailable > 0) {
            byte[] appData = new byte[appDataAvailable];
            tlsPskServerProtocol.readInput(appData, 0, appDataAvailable);
            out.add(Unpooled.wrappedBuffer(appData));
        }
    }

    private void writeOutputIfAvailable(ChannelHandlerContext ctx) {
        final int availableOutputBytes = tlsPskServerProtocol.getAvailableOutputBytes();
        // output is available immediately (handshake not complete), pipe that back to the client right away
        if (availableOutputBytes != 0) {
            byte[] outputBytes = new byte[availableOutputBytes];
            tlsPskServerProtocol.readOutput(outputBytes, 0, availableOutputBytes);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(outputBytes))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        tlsPskServer =
                new ZuulPskServer(new JcaTlsCryptoProvider().create(new SecureRandom()), registry, externalTlsPskProvider, ctx);
        tlsPskServerProtocol = new TlsPskServerProtocol();
        tlsPskServerProtocol.accept(tlsPskServer);
        super.channelRegistered(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    private static byte[] readDirect(ByteBuf byteBufMsg) {
        int length = byteBufMsg.readableBytes();
        byte[] dest = new byte[length];
        byteBufMsg.readSlice(length).getBytes(0, dest);
        return dest;
    }

    /**
     * Returns the name of the current application-level protocol.
     * Returns:
     * the protocol name or null if application-level protocol has not been negotiated
     */
    public String getApplicationProtocol() {
        return tlsPskServer!=null ? tlsPskServer.getApplicationProtocol() : null;
    }

    public SSLSession getSession() {
        return tlsPskServerProtocol!=null ? tlsPskServerProtocol.getSSLSession() : null;
    }

    static class ZuulPskServer extends AbstractTlsServer {

        private static final Logger LOGGER = LoggerFactory.getLogger(ZuulPskServer.class);

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


        public ZuulPskServer(
                TlsCrypto crypto,
                Registry registry,
                ExternalTlsPskProvider externalTlsPskProvider, ChannelHandlerContext ctx) {
            super(crypto);
            this.pskTimings = new PSKTimings(registry);
            this.externalTlsPskProvider = externalTlsPskProvider;
            this.ctx = ctx;
        }

        @Override
        public TlsCredentials getCredentials() {
            return null;
        }

        @Override
        protected Vector getProtocolNames() {
            Vector protocolNames = new Vector();
            protocolNames.addElement(ProtocolName.HTTP_1_1);
            protocolNames.addElement(ProtocolName.HTTP_2_TLS);
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
                    SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP.keySet().stream()
                            .mapToInt(Number::intValue)
                            .toArray());
        }

        @Override
        public ProtocolVersion getServerVersion() throws IOException {
            return super.getServerVersion();
        }

        @Override
        @SneakyThrows // TODO: Ask BC folks to see if getExternalPSK can throw a checked exception
        public TlsPSKExternal getExternalPSK(Vector clientPskIdentities) {
            byte[] clientPskIdentity = ((PskIdentity)clientPskIdentities.get(0)).getIdentity();
            byte[] psk;
            try{
                this.ctx.channel().attr(CLIENT_PSK_IDENTITY_ATTRIBUTE_KEY).set(new ClientPSKIdentityInfo(clientPskIdentity));
                psk = externalTlsPskProvider.provide(clientPskIdentity, this.context.getSecurityParametersHandshake().getClientRandom());
            }catch (PskCreationFailureException e) {
                throw switch (e.getTlsAlertMessage()) {
                    case unknown_psk_identity -> new TlsFatalAlert(AlertDescription.unknown_psk_identity, "Unknown or null client PSk identity");
                    case decrypt_error -> new TlsFatalAlert(AlertDescription.decrypt_error, "Invalid or expired client PSk identity");
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
            ProtocolName protocolName =
                    context.getSecurityParametersConnection().getApplicationProtocol();
            if (protocolName!=null) {
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

    static class TlsPskServerProtocol extends TlsServerProtocol {

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
                    return SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP.get(
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
}