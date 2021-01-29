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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.ChannelUtils;
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
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;

/**
 * Stores info about the client and server's SSL certificates in the context, after a successful handshake.
 * <p>
 * User: michaels@netflix.com Date: 3/29/16 Time: 10:48 AM
 */
public class SslHandshakeInfoHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<SslHandshakeInfo> ATTR_SSL_INFO = AttributeKey.newInstance("_ssl_handshake_info");
    private static final Logger logger = LoggerFactory.getLogger(SslHandshakeInfoHandler.class);

    private final Registry spectatorRegistry;
    private final boolean isSSlFromIntermediary;

    public SslHandshakeInfoHandler(Registry spectatorRegistry, boolean isSSlFromIntermediary) {
        this.spectatorRegistry = checkNotNull(spectatorRegistry);
        this.isSSlFromIntermediary = isSSlFromIntermediary;
    }

    @VisibleForTesting
    SslHandshakeInfoHandler() {
        spectatorRegistry = null;
        isSSlFromIntermediary = false;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            try {
                SslHandshakeCompletionEvent sslEvent = (SslHandshakeCompletionEvent) evt;
                if (sslEvent.isSuccess()) {

                    CurrentPassport.fromChannel(ctx.channel()).add(PassportState.SERVER_CH_SSL_HANDSHAKE_COMPLETE);

                    SslHandler sslhandler = ctx.channel().pipeline().get(SslHandler.class);
                    SSLSession session = sslhandler.engine().getSession();

                    ClientAuth clientAuth = whichClientAuthEnum(sslhandler);

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

                    SslHandshakeInfo info = new SslHandshakeInfo(isSSlFromIntermediary, session.getProtocol(),
                            session.getCipherSuite(), clientAuth, serverCert, peerCert);
                    ctx.channel().attr(ATTR_SSL_INFO).set(info);

                    // Metrics.
                    incrementCounters(sslEvent, info);

                    logger.debug("Successful SSL Handshake: {}", info);
                } else {
                    String clientIP = ctx.channel().attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get();
                    Throwable cause = sslEvent.cause();

                    PassportState passportState = CurrentPassport.fromChannel(ctx.channel()).getState();
                    if (cause instanceof ClosedChannelException &&
                            (PassportState.SERVER_CH_INACTIVE.equals(passportState)
                                    || PassportState.SERVER_CH_IDLE_TIMEOUT.equals(passportState))) {
                        // Either client closed the connection without/before having completed a handshake, or
                        // the connection idle timed-out before handshake.
                        // NOTE: we were seeing a lot of these in prod and can repro by just telnetting to port and then closing terminal
                        // without sending anything.
                        // So don't treat these as SSL handshake failures.
                        logger.debug("Client closed connection or it idle timed-out without doing an ssl handshake. "
                                + ", client_ip = " + clientIP
                                + ", channel_info = " + ChannelUtils.channelInfoForLogging(ctx.channel()));
                    } else if (cause instanceof SSLException && cause.getMessage().contains("handshake timed out")) {
                        logger.debug("Client timed-out doing the ssl handshake. "
                                + ", client_ip = " + clientIP
                                + ", channel_info = " + ChannelUtils.channelInfoForLogging(ctx.channel()));
                    } else if (cause instanceof SSLException
                            && cause.getMessage().contains("failure when writing TLS control frames")) {
                        // This can happen if the ClientHello is sent followed  by a RST packet, before we can respond.
                        logger.debug("Client terminated handshake early."
                                + ", client_ip = " + clientIP
                                + ", channel_info = " + ChannelUtils.channelInfoForLogging(ctx.channel()));
                    } else {
                        String msg = "Unsuccessful SSL Handshake: " + sslEvent
                                + ", client_ip = " + clientIP
                                + ", channel_info = " + ChannelUtils.channelInfoForLogging(ctx.channel())
                                + ", error = " + cause;
                        if (cause instanceof ClosedChannelException) {
                            logger.debug(msg);
                        } else {
                            logger.debug(msg, cause);
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
                spectatorRegistry.counter("zuul.sni.parse.failure", cause != null ? cause.getMessage() : "UNKNOWN")
                        .increment();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    private ClientAuth whichClientAuthEnum(SslHandler sslhandler) {
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
        if (spectatorRegistry == null) {
            // May be null for testing.
            return;
        }
        try {
            if (sslHandshakeCompletionEvent.isSuccess()) {
                String proto = handshakeInfo.getProtocol().length() > 0 ? handshakeInfo.getProtocol() : "unknown";
                String ciphsuite =
                        handshakeInfo.getCipherSuite().length() > 0 ? handshakeInfo.getCipherSuite() : "unknown";
                spectatorRegistry.counter("server.ssl.handshake",
                        "success", String.valueOf(sslHandshakeCompletionEvent.isSuccess()),
                        "protocol", String.valueOf(proto),
                        "ciphersuite", String.valueOf(ciphsuite),
                        "clientauth", String.valueOf(handshakeInfo.getClientAuthRequirement())
                )
                        .increment();
            } else {
                spectatorRegistry.counter("server.ssl.handshake",
                        "success", String.valueOf(sslHandshakeCompletionEvent.isSuccess()),
                        "failure_cause", String.valueOf(sslHandshakeCompletionEvent.cause())
                )
                        .increment();
            }
        } catch (Exception e) {
            logger.error("Error incrememting counters for SSL handshake!", e);
        }
    }
}
