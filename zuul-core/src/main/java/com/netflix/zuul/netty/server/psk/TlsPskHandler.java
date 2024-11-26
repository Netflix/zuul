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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLSession;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;

public class TlsPskHandler extends ChannelDuplexHandler {

    public static final Map<Integer, String> SUPPORTED_TLS_PSK_CIPHER_SUITE_MAP = Map.of(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            "TLS_AES_128_GCM_SHA256",
            CipherSuite.TLS_AES_256_GCM_SHA384,
            "TLS_AES_256_GCM_SHA384");
    public static final AttributeKey<ClientPSKIdentityInfo> CLIENT_PSK_IDENTITY_ATTRIBUTE_KEY =
            AttributeKey.newInstance("_client_psk_identity_info");
    public static final SecureRandom secureRandom = new SecureRandom();

    private final Registry registry;
    private final ExternalTlsPskProvider externalTlsPskProvider;
    private final Set<ProtocolName> supportedApplicationProtocols;
    private final TlsPskServerProtocol tlsPskServerProtocol;

    private ZuulPskServer tlsPskServer;

    public TlsPskHandler(Registry registry, ExternalTlsPskProvider externalTlsPskProvider, Set<ProtocolName> supportedApplicationProtocols) {
        super();
        this.registry = registry;
        this.externalTlsPskProvider = externalTlsPskProvider;
        this.supportedApplicationProtocols = supportedApplicationProtocols;
        this.tlsPskServerProtocol = new TlsPskServerProtocol();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof ByteBuf byteBufMsg)) {
            ReferenceCountUtil.safeRelease(msg);
            promise.setFailure(new IllegalStateException("Failed to write message on the channel. Message is not a ByteBuf"));
            return;
        }
        byte[] appDataBytes = TlsPskUtils.getAppDataBytesAndRelease(byteBufMsg);
        tlsPskServerProtocol.writeApplicationData(appDataBytes, 0, appDataBytes.length);
        int availableOutputBytes = tlsPskServerProtocol.getAvailableOutputBytes();
        if (availableOutputBytes != 0) {
            byte[] outputBytes = new byte[availableOutputBytes];
            tlsPskServerProtocol.readOutput(outputBytes, 0, availableOutputBytes);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(outputBytes), promise)
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.pipeline().addBefore(ctx.name(), "tls_psk_handler", new TlsPskDecoder(tlsPskServerProtocol));
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        tlsPskServer =
                new ZuulPskServer(new JcaTlsCryptoProvider().create(secureRandom), registry, externalTlsPskProvider, ctx, supportedApplicationProtocols);
        tlsPskServerProtocol.accept(tlsPskServer);
        super.channelRegistered(ctx);
    }

    /**
     * Returns the name of the current application-level protocol.
     * Returns:
     * the protocol name or null if application-level protocol has not been negotiated
     */
    public String getApplicationProtocol() {
        return tlsPskServer != null ? tlsPskServer.getApplicationProtocol() : null;
    }

    public SSLSession getSession() {
        return tlsPskServerProtocol != null ? tlsPskServerProtocol.getSSLSession() : null;
    }

}