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
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Created by saroskar on 3/15/16.
 */
public class PooledConnection {

    protected static final AttributeKey<PooledConnection> CHANNEL_ATTR = AttributeKey.newInstance("_pooled_connection");
    public static final String READ_TIMEOUT_HANDLER_NAME = "readTimeoutHandler";

    private final DiscoveryResult server;
    private final Channel channel;
    private final ClientChannelManager channelManager;
    private final long creationTS;
    private final Counter closeConnCounter;
    private final Counter closeWrtBusyConnCounter;

    private static final Logger LOG = LoggerFactory.getLogger(PooledConnection.class);

    /**
     * Connection State
     */
    public enum ConnectionState {
        /**
         * valid state in pool
         */
        WRITE_READY,
        /**
         * Can not be put in pool
         */
        WRITE_BUSY
    }


    private ConnectionState connectionState;
    private long usageCount = 0;
    private long reqStartTime;
    private boolean inPool = false;
    private boolean shouldClose = false;
    private boolean released = false;

    public PooledConnection(final Channel channel, final DiscoveryResult server, final ClientChannelManager channelManager,
                     final Counter closeConnCounter, 
                     final Counter closeWrtBusyConnCounter)
    {
        this.channel = channel;
        this.server = server;
        this.channelManager = channelManager;
        this.creationTS = System.currentTimeMillis();
        this.closeConnCounter = closeConnCounter;
        this.closeWrtBusyConnCounter = closeWrtBusyConnCounter;
        
        this.connectionState = ConnectionState.WRITE_READY;

        // Store ourself as an attribute on the underlying Channel.
        channel.attr(CHANNEL_ATTR).set(this);
    }

    public void setInUse()
    {
        this.connectionState = ConnectionState.WRITE_BUSY;
        this.released = false;
    }

    public void setConnectionState(ConnectionState state) {
        this.connectionState = state;
    }

    public static PooledConnection getFromChannel(Channel ch)
    {
        return ch.attr(CHANNEL_ATTR).get();
    }

    public ConnectionPoolConfig getConfig()
    {
        return this.channelManager.getConfig();
    }

    public DiscoveryResult getServer()
    {
        return server;
    }

    public Channel getChannel() {
        return channel;
    }

    public long getUsageCount()
    {
        return usageCount;
    }

    public void incrementUsageCount()
    {
        this.usageCount++;
    }

    public long getCreationTS() {
        return creationTS;
    }

    public long getAgeInMillis() {
        return System.currentTimeMillis() - creationTS;
    }

    public void startRequestTimer() {
        reqStartTime = System.nanoTime();
    }

    public long stopRequestTimer() {
        final long responseTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - reqStartTime);
        server.noteResponseTime(responseTime);
        return responseTime;
    }

    public boolean isActive() {
        return (channel.isActive() && channel.isRegistered());
    }

    public boolean isInPool()
    {
        return inPool;
    }

    public void setInPool(boolean inPool)
    {
        this.inPool = inPool;
    }

    public boolean isShouldClose()
    {
        return shouldClose;
    }

    public void flagShouldClose()
    {
        this.shouldClose = true;
    }

    public ChannelFuture close() {
        server.decrementOpenConnectionsCount();
        closeConnCounter.increment();
        return channel.close();
    }

    public void updateServerStats() {
        server.decrementOpenConnectionsCount();
        server.stopPublishingStats();
    }

    public ChannelFuture closeAndRemoveFromPool()
    {
        channelManager.remove(this);
        return this.close();
    }

    public void release()
    {
        if (released) {
            return;
        }

        if (isActive()) {
            if (connectionState != ConnectionState.WRITE_READY) {
                closeWrtBusyConnCounter.increment();
            }
        }
        
        if (! isShouldClose() && connectionState != ConnectionState.WRITE_READY) {
            CurrentPassport passport = CurrentPassport.fromChannel(channel);
            LOG.info("Error - Attempt to put busy connection into the pool = " + this.toString()
                    + ", " + String.valueOf(passport));
            this.shouldClose = true;
        }

        // reset the connectionState
        connectionState = ConnectionState.WRITE_READY;
        released = true;
        channelManager.release(this);
    }

    public void removeReadTimeoutHandler()
    {
        // Remove (and therefore destroy) the readTimeoutHandler when we release the
        // channel back to the pool. As don't want it timing-out when it's not in use.
        final ChannelPipeline pipeline = getChannel().pipeline();
        removeHandlerFromPipeline(READ_TIMEOUT_HANDLER_NAME, pipeline);
    }

    private void removeHandlerFromPipeline(String handlerName, ChannelPipeline pipeline)
    {
        if (pipeline.get(handlerName) != null) {
            pipeline.remove(handlerName);
        }
    }

    public void startReadTimeoutHandler(Duration readTimeout)
    {
        channel.pipeline().addBefore("originNettyLogger", READ_TIMEOUT_HANDLER_NAME,
                new ReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }


    @Override
    public String toString()
    {
        return "PooledConnection{" +
                "channel=" + channel +
                ", usageCount=" + usageCount +
                '}';
    }
}
