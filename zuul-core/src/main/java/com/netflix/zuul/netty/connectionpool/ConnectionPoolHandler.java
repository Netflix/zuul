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

import com.netflix.spectator.api.Counter;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import static com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason.SESSION_COMPLETE;

/**
 * User: michaels@netflix.com
 * Date: 6/23/16
 * Time: 1:57 PM
 */
@ChannelHandler.Sharable
public class ConnectionPoolHandler extends ChannelDuplexHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPoolHandler.class);

    public static final String METRIC_PREFIX = "connectionpool";
    
    private final String originName;
    private final Counter idleCounter;
    private final Counter inactiveCounter;
    private final Counter errorCounter;

    public ConnectionPoolHandler(String originName) {
        if (originName == null) throw new IllegalArgumentException("Null originName passed to constructor!");
        this.originName = originName;
        this.idleCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_idle", originName);
        this.inactiveCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_inactive", originName);
        this.errorCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_error", originName);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // First let other handlers do their thing ...
        // super.userEventTriggered(ctx, evt);

        if (evt instanceof IdleStateEvent) {
            // Log some info about this.
            idleCounter.increment();
            final String msg = "Origin channel for origin - " + originName + " - idle timeout has fired. " + ChannelUtils.channelInfoForLogging(ctx.channel());
            closeConnection(ctx, msg);
        }
        else if (evt instanceof CompleteEvent) {
            // The HttpLifecycleChannelHandler instance will fire this event when either a response has finished being written, or
            // the channel is no longer active or disconnected.
            // Return the connection to pool.
            final CompleteReason reason = ((CompleteEvent) evt).getReason();
            if (reason == SESSION_COMPLETE) {
                final PooledConnection conn = PooledConnection.getFromChannel(ctx.channel());
                if (conn != null) {
                    conn.setConnectionState(PooledConnection.ConnectionState.WRITE_READY);
                    conn.release();
                }
            } else {
                final String msg = "Origin channel for origin - " + originName + " - completed with reason "
                        + reason.name() + ", " + ChannelUtils.channelInfoForLogging(ctx.channel());
                closeConnection(ctx, msg);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //super.exceptionCaught(ctx, cause);
        errorCounter.increment();
        final String mesg = "Exception on Origin channel for origin - " + originName + ". "
                + ChannelUtils.channelInfoForLogging(ctx.channel()) + " - " +
                cause.getClass().getCanonicalName() + ": " + cause.getMessage();
        closeConnection(ctx, mesg);

        if (LOG.isDebugEnabled()) {
            LOG.debug(mesg, cause);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //super.channelInactive(ctx);
        inactiveCounter.increment();
        final String msg = "Client channel for origin - " + originName + " - inactive event has fired. "
                + ChannelUtils.channelInfoForLogging(ctx.channel());
        closeConnection(ctx, msg);
    }

    private void closeConnection(ChannelHandlerContext ctx, String msg) {
        PooledConnection conn = PooledConnection.getFromChannel(ctx.channel());
        if (conn != null) {
            if (LOG.isDebugEnabled()) {
                msg = msg + " Closing the PooledConnection and releasing."
                        + " ASG: " + String.valueOf(conn.getServerKey().getASGName()
                        + ", host=" + String.valueOf(conn.getServerKey().getHostName()));
                LOG.debug(msg);
            }
            flagCloseAndReleaseConnection(conn);
        }
        else {
            // If somehow we don't have a PooledConnection instance attached to this channel, then
            // close the channel directly.
            LOG.warn(msg + " But no PooledConnection attribute. So just closing Channel.");
            ctx.close();
        }
    }

    private void flagCloseAndReleaseConnection(PooledConnection pooledConnection) {
        if (pooledConnection.isInPool()) {
            pooledConnection.closeAndRemoveFromPool();
        }
        else {
            pooledConnection.flagShouldClose();
            pooledConnection.release();
        }
    }
}
