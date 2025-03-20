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

package com.netflix.zuul.netty.server.ssl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.server.psk.ClientPSKIdentityInfo;
import com.netflix.zuul.netty.server.psk.TlsPskHandler;
import com.netflix.zuul.netty.server.psk.ZuulPskServer;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.AttributeKey;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores info about the client and server's SSL certificates in the context, after a successful handshake.
 * <p>
 * User: michaels@netflix.com Date: 3/29/16 Time: 10:48 AM
 */
public class SslHandshakeInfoHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<SslHandshakeInfo> ATTR_SSL_INFO = AttributeKey.newInstance("_ssl_handshake_info");
    private static final Logger logger = LoggerFactory.getLogger(SslHandshakeInfoHandler.class);

    // extracts reason string from SSL errors formatted in the open ssl style
    // error:[error code]:[library name]:OPENSSL_internal:[reason string]
    // see https://github.com/google/boringssl/blob/d206f3db6ac2b74e8949ddd9947b94a5424d6a1d/include/openssl/err.h#L231
    private static final Pattern OPEN_SSL_PATTERN = Pattern.compile("OPENSSL_internal:(.+)");

    private final Registry spectatorRegistry;
    private final boolean isSSlFromIntermediary;

    public SslHandshakeInfoHandler(Registry spectatorRegistry, boolean isSSlFromIntermediary) {
        this.spectatorRegistry = Preconditions.checkNotNull(spectatorRegistry);
        this.isSSlFromIntermediary = isSSlFromIntermediary;
    }

    @VisibleForTesting
    SslHandshakeInfoHandler() {
        spectatorRegistry = new NoopRegistry();
        isSSlFromIntermediary = false;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            try {
                SslHandshakeCompletionEvent sslEvent = (SslHandshakeCompletionEvent) evt;
                if (sslEvent.isSuccess()) {

                    CurrentPassport.fromChannel(ctx.channel()).add(PassportState.SERVER_CH_SSL_HANDSHAKE_COMPLETE);

                    SSLSession session = getSSLSession(ctx);
                    if (session == null) {
                        logger.warn("Error getting the SSL handshake info. SSLSession is null");
                        return;
                    }

                    ClientAuth clientAuth = whichClientAuthEnum(ctx);

                    Certificate serverCert = null;
                    X509Certificate peerCert = null;

                    if ((clientAuth == ClientAuth.REQUIRE || clientAuth == ClientAuth.OPTIONAL)
                            && session.getPeerCertificates() != null
                            && session.getPeerCertificates().length > 0) {
                        peerCert = (X509Certificate) session.getPeerCertificates()[0];
                    }
                    if (session.getLocalCertificates() != null && session.getLocalCertificates().length > 0) {
                        serverCert = session.getLocalCertificates()[0];
                    }

                    // if attribute is true, then true. If null or false then false
                    boolean tlsHandshakeUsingExternalPSK = ctx.channel()
                            .attr(ZuulPskServer.TLS_HANDSHAKE_USING_EXTERNAL_PSK)
                            .get()
                            .equals(Boolean.TRUE);

                    ClientPSKIdentityInfo clientPSKIdentityInfo = ctx.channel()
                            .attr(TlsPskHandler.CLIENT_PSK_IDENTITY_ATTRIBUTE_KEY)
                            .get();

                    SslHandshakeInfo info = new SslHandshakeInfo(
                            isSSlFromIntermediary,
                            session.getProtocol(),
                            session.getCipherSuite(),
                            clientAuth,
                            serverCert,
                            peerCert,
                            tlsHandshakeUsingExternalPSK,
                            clientPSKIdentityInfo);
                    ctx.channel().attr(ATTR_SSL_INFO).set(info);

                    // Metrics.
                    incrementCounters(sslEvent, info);

                    logger.debug("Successful SSL Handshake: {}", info);
                } else {
                    String clientIP = ctx.channel()
                            .attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS)
                            .get();
                    Throwable cause = sslEvent.cause();

                    PassportState passportState =
                            CurrentPassport.fromChannel(ctx.channel()).getState();
                    if (cause instanceof ClosedChannelException
                            && (passportState.equals(PassportState.SERVER_CH_INACTIVE)
                                    || passportState.equals(PassportState.SERVER_CH_IDLE_TIMEOUT))) {
                        // Either client closed the connection without/before having completed a handshake, or
                        // the connection idle timed-out before handshake.
                        // NOTE: we were seeing a lot of these in prod and can repro by just telnetting to port and then
                        // closing terminal
                        // without sending anything.
                        // So don't treat these as SSL handshake failures.
                        logger.debug(
                                "Client closed connection or it idle timed-out without doing an ssl handshake. ,"
                                        + " client_ip = {}, channel_info = {}",
                                clientIP,
                                ChannelUtils.channelInfoForLogging(ctx.channel()));
                    } else if (cause instanceof SSLException
                            && cause.getMessage().contains("handshake timed out")) {
                        logger.debug(
                                "Client timed-out doing the ssl handshake. , client_ip = {}, channel_info = {}",
                                clientIP,
                                ChannelUtils.channelInfoForLogging(ctx.channel()));
                    } else if (cause instanceof SSLException
                            && cause.getMessage().contains("failure when writing TLS control frames")) {
                        // This can happen if the ClientHello is sent followed  by a RST packet, before we can respond.
                        logger.debug(
                                "Client terminated handshake early., client_ip = {}, channel_info = {}",
                                clientIP,
                                ChannelUtils.channelInfoForLogging(ctx.channel()));
                    } else {
                        if (logger.isDebugEnabled()) {
                            String msg = "Unsuccessful SSL Handshake: " + sslEvent
                                    + ", client_ip = " + clientIP
                                    + ", channel_info = " + ChannelUtils.channelInfoForLogging(ctx.channel())
                                    + ", error = " + cause;
                            if (cause instanceof ClosedChannelException) {
                                logger.debug(msg);
                            } else {
                                logger.debug(msg, cause);
                            }
                        }
                        incrementCounters(sslEvent, null);
                    }
                }
            } catch (Throwable e) {
                logger.warn("Error getting the SSL handshake info.", e);
            } finally {
                // Now remove this handler from the pipeline as no longer needed once the ssl handshake has completed.
                ctx.pipeline().remove(this);
            }
        } else if (evt instanceof SslCloseCompletionEvent) {
            // TODO - increment a separate metric for this event?
        } else if (evt instanceof SniCompletionEvent) {
            logger.debug("SNI Parsing Complete: {}", evt);

            SniCompletionEvent sniCompletionEvent = (SniCompletionEvent) evt;
            if (sniCompletionEvent.isSuccess()) {
                spectatorRegistry.counter("zuul.sni.parse.success").increment();
            } else {
                Throwable cause = sniCompletionEvent.cause();
                spectatorRegistry
                        .counter("zuul.sni.parse.failure", "cause", cause != null ? cause.getMessage() : "UNKNOWN")
                        .increment();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    private SSLSession getSSLSession(ChannelHandlerContext ctx) {
        SslHandler sslhandler = ctx.channel().pipeline().get(SslHandler.class);
        if (sslhandler != null) {
            return sslhandler.engine().getSession();
        }
        TlsPskHandler tlsPskHandler = ctx.channel().pipeline().get(TlsPskHandler.class);
        if (tlsPskHandler != null) {
            return tlsPskHandler.getSession();
        }
        return null;
    }

    private ClientAuth whichClientAuthEnum(ChannelHandlerContext ctx) {
        SslHandler sslhandler = ctx.channel().pipeline().get(SslHandler.class);
        if (sslhandler == null) {
            return ClientAuth.NONE;
        }

        ClientAuth clientAuth;
        if (sslhandler.engine().getNeedClientAuth()) {
            clientAuth = ClientAuth.REQUIRE;
        } else if (sslhandler.engine().getWantClientAuth()) {
            clientAuth = ClientAuth.OPTIONAL;
        } else {
            clientAuth = ClientAuth.NONE;
        }
        return clientAuth;
    }

    private void incrementCounters(
            SslHandshakeCompletionEvent sslHandshakeCompletionEvent, SslHandshakeInfo handshakeInfo) {
        try {
            if (sslHandshakeCompletionEvent.isSuccess()) {
                String proto = handshakeInfo.getProtocol().length() > 0 ? handshakeInfo.getProtocol() : "unknown";
                String ciphsuite =
                        handshakeInfo.getCipherSuite().length() > 0 ? handshakeInfo.getCipherSuite() : "unknown";
                spectatorRegistry
                        .counter(
                                "server.ssl.handshake",
                                "success",
                                "true",
                                "protocol",
                                proto,
                                "ciphersuite",
                                ciphsuite,
                                "clientauth",
                                String.valueOf(handshakeInfo.getClientAuthRequirement()))
                        .increment();
            } else {
                spectatorRegistry
                        .counter(
                                "server.ssl.handshake",
                                "success",
                                "false",
                                "failure_cause",
                                getFailureCause(sslHandshakeCompletionEvent.cause()))
                        .increment();
            }
        } catch (Exception e) {
            logger.error("Error incrementing counters for SSL handshake!", e);
        }
    }

    @VisibleForTesting
    String getFailureCause(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return throwable.toString();
        }

        Matcher matcher = OPEN_SSL_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : message;
    }
}
