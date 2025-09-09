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

package com.netflix.zuul.netty.connectionpool;

import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;

import com.netflix.spectator.api.Spectator;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.origins.OriginName;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 6/23/16
 * Time: 1:57 PM
 */
@ChannelHandler.Sharable
public class ConnectionPoolHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPoolHandler.class);

    private final ConnectionPoolMetrics metrics;
    private final OriginName originName;


    @Deprecated
    public ConnectionPoolHandler(OriginName originName) {
        this(ConnectionPoolMetrics.create(Objects.requireNonNull(originName), Spectator.globalRegistry()));
    }

    public ConnectionPoolHandler(ConnectionPoolMetrics metrics) {
        this.originName = metrics.originName();
        this.metrics = metrics;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // First let other handlers do their thing ...
        // super.userEventTriggered(ctx, evt);

        if (evt instanceof IdleStateEvent) {
            // Log some info about this.
            metrics.idleCounter().increment();
            String msg = "Origin channel for origin - " + originName + " - idle timeout has fired. "
                    + ChannelUtils.channelInfoForLogging(ctx.channel());
            closeConnection(ctx, msg);
        } else if (evt instanceof CompleteEvent completeEvt) {
            // The HttpLifecycleChannelHandler instance will fire this event when either a response has finished being
            // written, or
            // the channel is no longer active or disconnected.
            // Return the connection to pool.
            CompleteReason reason = completeEvt.getReason();
            if (reason == CompleteReason.SESSION_COMPLETE) {
                PooledConnection conn = PooledConnection.getFromChannel(ctx.channel());
                if (conn != null) {
                    if ("close".equalsIgnoreCase(getConnectionHeader(completeEvt))) {
                        String msg = "Origin channel for origin - " + originName
                                + " - completed because of expired keep-alive. "
                                + ChannelUtils.channelInfoForLogging(ctx.channel());
                        metrics.headerCloseCounter().increment();
                        closeConnection(ctx, msg);
                    } else {
                        conn.setConnectionState(PooledConnection.ConnectionState.WRITE_READY);
                        conn.release();
                    }
                }
            } else {
                String msg = "Origin channel for origin - " + originName + " - completed with reason " + reason.name()
                        + ", " + ChannelUtils.channelInfoForLogging(ctx.channel());
                closeConnection(ctx, msg);
            }
        } else if (evt instanceof SslCloseCompletionEvent event) {
            metrics.sslCloseCompletionCounter().increment();
            String msg = "Origin channel for origin - " + originName + " - received SslCloseCompletionEvent " + event
                    + ". " + ChannelUtils.channelInfoForLogging(ctx.channel());
            closeConnection(ctx, msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // super.exceptionCaught(ctx, cause);
        metrics.errorCounter().increment();
        String mesg = "Exception on Origin channel for origin - " + originName + ". "
                + ChannelUtils.channelInfoForLogging(ctx.channel()) + " - "
                + cause.getClass().getCanonicalName()
                + ": " + cause.getMessage();
        closeConnection(ctx, mesg);

        if (LOG.isDebugEnabled()) {
            LOG.debug(mesg, cause);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // super.channelInactive(ctx);
        metrics.inactiveCounter().increment();
        String msg = "Client channel for origin - " + originName + " - inactive event has fired. "
                + ChannelUtils.channelInfoForLogging(ctx.channel());
        closeConnection(ctx, msg);
    }

    private void closeConnection(ChannelHandlerContext ctx, String msg) {
        PooledConnection conn = PooledConnection.getFromChannel(ctx.channel());
        if (conn != null) {
            if (LOG.isDebugEnabled()) {
                msg = msg + " Closing the PooledConnection and releasing. conn={}";
                LOG.debug(msg, conn);
            }
            flagCloseAndReleaseConnection(conn);
        } else {
            // If somehow we don't have a PooledConnection instance attached to this channel, then
            // close the channel directly.
            LOG.warn("{} But no PooledConnection attribute. So just closing Channel.", msg);
            ctx.close();
        }
    }

    private void flagCloseAndReleaseConnection(PooledConnection pooledConnection) {
        if (pooledConnection.isInPool()) {
            pooledConnection.closeAndRemoveFromPool();
        } else {
            pooledConnection.flagShouldClose();
            pooledConnection.release();
        }
    }

    private static String getConnectionHeader(CompleteEvent completeEvt) {
        HttpResponse response = completeEvt.getResponse();
        if (response != null) {
            return response.headers().get(HttpHeaderNames.CONNECTION);
        }

        return null;
    }
}
