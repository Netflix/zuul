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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import com.netflix.client.config.IClientConfig;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.discovery.DynamicServerResolver;
import com.netflix.zuul.discovery.ResolverResult;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.insights.PassportStateHttpClientHandler;
import com.netflix.zuul.netty.server.OriginResponseReceiver;
import com.netflix.zuul.origins.OriginName;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.resolver.Resolver;
import com.netflix.zuul.resolver.ResolverListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: michaels@netflix.com
 * Date: 7/8/16
 * Time: 12:39 PM
 */
public class DefaultClientChannelManager implements ClientChannelManager {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultClientChannelManager.class);

    public static final String METRIC_PREFIX = "connectionpool";

    private final Resolver<DiscoveryResult> dynamicServerResolver;
    private final ConnectionPoolConfig connPoolConfig;
    private final IClientConfig clientConfig;
    private final Registry spectatorRegistry;

    private final OriginName originName;

    private static final Throwable SHUTTING_DOWN_ERR =
            new IllegalStateException("ConnectionPool is shutting down now.");
    private volatile boolean shuttingDown = false;

    private final Counter createNewConnCounter;
    private final Counter createConnSucceededCounter;
    private final Counter createConnFailedCounter;

    private final Counter closeConnCounter;
    private final Counter requestConnCounter;
    private final Counter closeAbovePoolHighWaterMarkCounter;
    private final Counter closeExpiredConnLifetimeCounter;
    private final Counter reuseConnCounter;
    private final Counter releaseConnCounter;
    private final Counter alreadyClosedCounter;
    private final Counter connTakenFromPoolIsNotOpen;
    private final Counter maxConnsPerHostExceededCounter;
    private final Counter closeWrtBusyConnCounter;
    private final PercentileTimer connEstablishTimer;
    private final AtomicInteger connsInPool;
    private final AtomicInteger connsInUse;

    private final ConcurrentHashMap<DiscoveryResult, IConnectionPool> perServerPools;

    private NettyClientConnectionFactory clientConnFactory;
    private OriginChannelInitializer channelInitializer;

    public static final String IDLE_STATE_HANDLER_NAME = "idleStateHandler";

    public DefaultClientChannelManager(OriginName originName, IClientConfig clientConfig, Registry spectatorRegistry) {
        this(originName, clientConfig, new DynamicServerResolver(clientConfig), spectatorRegistry);
    }

    public DefaultClientChannelManager(
            OriginName originName,
            IClientConfig clientConfig,
            Resolver<DiscoveryResult> resolver,
            Registry spectatorRegistry) {
        this.originName = Objects.requireNonNull(originName, "originName");
        this.dynamicServerResolver = resolver;

        String metricId = originName.getMetricId();

        this.clientConfig = clientConfig;
        this.spectatorRegistry = spectatorRegistry;
        this.perServerPools = new ConcurrentHashMap<>(200);

        this.connPoolConfig = new ConnectionPoolConfigImpl(originName, this.clientConfig);

        this.createNewConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_create", metricId);
        this.createConnSucceededCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_create_success", metricId);
        this.createConnFailedCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_create_fail", metricId);

        this.closeConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_close", metricId);
        this.closeAbovePoolHighWaterMarkCounter =
                SpectatorUtils.newCounter(METRIC_PREFIX + "_closeAbovePoolHighWaterMark", metricId);
        this.closeExpiredConnLifetimeCounter =
                SpectatorUtils.newCounter(METRIC_PREFIX + "_closeExpiredConnLifetime", metricId);
        this.requestConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_request", metricId);
        this.reuseConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_reuse", metricId);
        this.releaseConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_release", metricId);
        this.alreadyClosedCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_alreadyClosed", metricId);
        this.connTakenFromPoolIsNotOpen = SpectatorUtils.newCounter(METRIC_PREFIX + "_fromPoolIsClosed", metricId);
        this.maxConnsPerHostExceededCounter =
                SpectatorUtils.newCounter(METRIC_PREFIX + "_maxConnsPerHostExceeded", metricId);
        this.closeWrtBusyConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_closeWrtBusyConnCounter", metricId);
        this.connEstablishTimer = PercentileTimer.get(
                spectatorRegistry, spectatorRegistry.createId(METRIC_PREFIX + "_createTiming", "id", metricId));
        this.connsInPool = SpectatorUtils.newGauge(METRIC_PREFIX + "_inPool", metricId, new AtomicInteger());
        this.connsInUse = SpectatorUtils.newGauge(METRIC_PREFIX + "_inUse", metricId, new AtomicInteger());
    }

    @Override
    public void init() {
        dynamicServerResolver.setListener(new ServerPoolListener());
        // Load channel initializer and conn factory.
        // We don't do this within the constructor because some subclass may not be initialized until post-construct.
        this.channelInitializer = createChannelInitializer(clientConfig, connPoolConfig, spectatorRegistry);
        this.clientConnFactory = createNettyClientConnectionFactory(connPoolConfig, channelInitializer);
    }

    protected OriginChannelInitializer createChannelInitializer(
            IClientConfig clientConfig, ConnectionPoolConfig connPoolConfig, Registry registry) {
        return new DefaultOriginChannelInitializer(connPoolConfig, registry);
    }

    protected NettyClientConnectionFactory createNettyClientConnectionFactory(
            ConnectionPoolConfig connPoolConfig, ChannelInitializer<? extends Channel> clientConnInitializer) {
        return new NettyClientConnectionFactory(connPoolConfig, clientConnInitializer);
    }

    @Override
    public ConnectionPoolConfig getConfig() {
        return connPoolConfig;
    }

    @Override
    public boolean isAvailable() {
        return dynamicServerResolver.hasServers();
    }

    @Override
    public boolean isCold() {
        return false;
    }

    @Override
    public int getInflightRequestsCount() {
        return this.channelInitializer.getHttpMetricsHandler().getInflightRequestsCount();
    }

    @Override
    public void shutdown() {
        this.shuttingDown = true;

        dynamicServerResolver.shutdown();

        for (IConnectionPool pool : perServerPools.values()) {
            pool.shutdown();
        }
    }

    /**
     * Gracefully shuts down a DefaultClientChannelManager by allowing in-flight requests to finish before closing the connections.
     * Idle connections in the connection pools are closed, and any connections associated with an in-flight request
     * will be closed upon trying to return the connection to the pool
     */
    @Override
    public void gracefulShutdown() {
        LOG.info("Starting a graceful shutdown of {}", clientConfig.getClientName());
        shuttingDown = true;
        dynamicServerResolver.shutdown();
        perServerPools.values().forEach(IConnectionPool::drain);
    }

    @Override
    public boolean release(final PooledConnection conn) {

        conn.stopRequestTimer();
        releaseConnCounter.increment();
        connsInUse.decrementAndGet();

        final DiscoveryResult discoveryResult = conn.getServer();
        updateServerStatsOnRelease(conn);

        boolean released = false;

        // if the connection has been around too long (i.e. too many requests), then close it
        // TODO(argha-c): Document what is a reasonable default here, and the class of origins that optimizes for
        final boolean connExpiredLifetime = conn.getUsageCount() > connPoolConfig.getMaxRequestsPerConnection();
        if (conn.isShouldClose() || connExpiredLifetime) {
            // Close and discard the connection, as it has been flagged (possibly due to receiving a non-channel error
            // like a 503).
            conn.setInPool(false);
            conn.close();
            if (connExpiredLifetime) {
                closeExpiredConnLifetimeCounter.increment();
                LOG.debug(
                        "[{}] closing conn lifetime expired, usage: {}",
                        conn.getChannel().id(),
                        conn.getUsageCount());
            } else {
                LOG.debug(
                        "[{}] closing conn flagged to be closed",
                        conn.getChannel().id());
            }

        } else if (discoveryResult.isCircuitBreakerTripped()) {
            LOG.debug(
                    "[{}] closing conn, server circuit breaker tripped",
                    conn.getChannel().id());
            // Don't put conns for currently circuit-tripped servers back into the pool.
            conn.setInPool(false);
            conn.close();
        } else if (!conn.isActive()) {
            LOG.debug("[{}] conn inactive, cleaning up", conn.getChannel().id());
            // Connection is already closed, so discard.
            alreadyClosedCounter.increment();
            // make sure to decrement OpenConnectionCounts
            conn.updateServerStats();
            conn.setInPool(false);
        } else {
            releaseHandlers(conn);

            // Attempt to return connection to the pool.
            IConnectionPool pool = perServerPools.get(discoveryResult);
            if (pool != null) {
                released = pool.release(conn);
            } else {
                // The pool for this server no longer exists (maybe due to it falling out of
                // discovery).
                conn.setInPool(false);
                released = false;
                conn.close();
            }

            LOG.debug("PooledConnection released: {}", conn);
        }

        return released;
    }

    protected void updateServerStatsOnRelease(final PooledConnection conn) {
        final DiscoveryResult discoveryResult = conn.getServer();
        discoveryResult.decrementActiveRequestsCount();
        discoveryResult.incrementNumRequests();
    }

    protected void releaseHandlers(PooledConnection conn) {
        final ChannelPipeline pipeline = conn.getChannel().pipeline();
        removeHandlerFromPipeline(OriginResponseReceiver.CHANNEL_HANDLER_NAME, pipeline);
        // The Outbound handler is always after the inbound handler, so look for it.
        ChannelHandlerContext passportStateHttpClientHandlerCtx =
                pipeline.context(PassportStateHttpClientHandler.OutboundHandler.class);
        pipeline.addAfter(
                passportStateHttpClientHandlerCtx.name(),
                IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(0, 0, connPoolConfig.getIdleTimeout(), TimeUnit.MILLISECONDS));
    }

    public static void removeHandlerFromPipeline(final String handlerName, final ChannelPipeline pipeline) {
        if (pipeline.get(handlerName) != null) {
            pipeline.remove(handlerName);
        }
    }

    @Override
    public boolean remove(PooledConnection conn) {
        if (conn == null) {
            return false;
        }
        if (!conn.isInPool()) {
            return false;
        }

        // Attempt to remove the connection from the pool.
        IConnectionPool pool = perServerPools.get(conn.getServer());
        if (pool != null) {
            return pool.remove(conn);
        } else {
            // The pool for this server no longer exists (maybe due to it failling out of
            // discovery).
            conn.setInPool(false);
            connsInPool.decrementAndGet();
            return false;
        }
    }

    @Override
    public Promise<PooledConnection> acquire(final EventLoop eventLoop) {
        return acquire(eventLoop, null, CurrentPassport.create(), new AtomicReference<>(), new AtomicReference<>());
    }

    @Override
    public Promise<PooledConnection> acquire(
            EventLoop eventLoop,
            @Nullable Object key,
            CurrentPassport passport,
            AtomicReference<DiscoveryResult> selectedServer,
            AtomicReference<? super InetAddress> selectedHostAddr) {

        if (shuttingDown) {
            Promise<PooledConnection> promise = eventLoop.newPromise();
            promise.setFailure(SHUTTING_DOWN_ERR);
            return promise;
        }

        // Choose the next load-balanced server.
        final DiscoveryResult chosenServer = dynamicServerResolver.resolve(key);

        // (argha-c): Always ensure the selected server is updated, since the call chain relies on this mutation.
        selectedServer.set(chosenServer);
        if (chosenServer == DiscoveryResult.EMPTY) {
            Promise<PooledConnection> promise = eventLoop.newPromise();
            promise.setFailure(
                    new OriginConnectException("No servers available", OutboundErrorType.NO_AVAILABLE_SERVERS));
            return promise;
        }

        // Now get the connection-pool for this server.
        IConnectionPool pool = perServerPools.computeIfAbsent(chosenServer, s -> {
            SocketAddress finalServerAddr = pickAddress(chosenServer);
            final ClientChannelManager clientChannelMgr = this;
            PooledConnectionFactory pcf = createPooledConnectionFactory(
                    chosenServer, clientChannelMgr, closeConnCounter, closeWrtBusyConnCounter);

            // Create a new pool for this server.
            return createConnectionPool(
                    chosenServer,
                    finalServerAddr,
                    clientConnFactory,
                    pcf,
                    connPoolConfig,
                    clientConfig,
                    createNewConnCounter,
                    createConnSucceededCounter,
                    createConnFailedCounter,
                    requestConnCounter,
                    reuseConnCounter,
                    connTakenFromPoolIsNotOpen,
                    closeAbovePoolHighWaterMarkCounter,
                    maxConnsPerHostExceededCounter,
                    connEstablishTimer,
                    connsInPool,
                    connsInUse);
        });

        return pool.acquire(eventLoop, passport, selectedHostAddr);
    }

    protected PooledConnectionFactory createPooledConnectionFactory(
            DiscoveryResult chosenServer,
            ClientChannelManager clientChannelMgr,
            Counter closeConnCounter,
            Counter closeWrtBusyConnCounter) {
        return ch ->
                new PooledConnection(ch, chosenServer, clientChannelMgr, closeConnCounter, closeWrtBusyConnCounter);
    }

    protected IConnectionPool createConnectionPool(
            DiscoveryResult discoveryResult,
            SocketAddress serverAddr,
            NettyClientConnectionFactory clientConnFactory,
            PooledConnectionFactory pcf,
            ConnectionPoolConfig connPoolConfig,
            IClientConfig clientConfig,
            Counter createNewConnCounter,
            Counter createConnSucceededCounter,
            Counter createConnFailedCounter,
            Counter requestConnCounter,
            Counter reuseConnCounter,
            Counter connTakenFromPoolIsNotOpen,
            Counter closeAbovePoolHighWaterMarkCounter,
            Counter maxConnsPerHostExceededCounter,
            PercentileTimer connEstablishTimer,
            AtomicInteger connsInPool,
            AtomicInteger connsInUse) {
        return new PerServerConnectionPool(
                discoveryResult,
                serverAddr,
                clientConnFactory,
                pcf,
                connPoolConfig,
                clientConfig,
                createNewConnCounter,
                createConnSucceededCounter,
                createConnFailedCounter,
                requestConnCounter,
                reuseConnCounter,
                connTakenFromPoolIsNotOpen,
                closeAbovePoolHighWaterMarkCounter,
                maxConnsPerHostExceededCounter,
                connEstablishTimer,
                connsInPool,
                connsInUse);
    }

    final class ServerPoolListener implements ResolverListener<DiscoveryResult> {
        @Override
        public void onChange(List<DiscoveryResult> removedSet) {
            if (!removedSet.isEmpty()) {
                LOG.debug(
                        "Removing connection pools for missing servers. name = {}. {} servers gone.",
                        originName,
                        removedSet.size());
                for (DiscoveryResult s : removedSet) {
                    IConnectionPool pool = perServerPools.remove(s);
                    if (pool != null) {
                        pool.shutdown();
                    }
                }
            }
        }
    }

    @Override
    public int getConnsInPool() {
        return connsInPool.get();
    }

    @Override
    public int getConnsInUse() {
        return connsInUse.get();
    }

    protected ConcurrentHashMap<DiscoveryResult, IConnectionPool> getPerServerPools() {
        return perServerPools;
    }

    @VisibleForTesting
    static SocketAddress pickAddressInternal(ResolverResult chosenServer, @Nullable OriginName originName) {
        String rawHost;
        int port;
        rawHost = chosenServer.getHost();
        port = chosenServer.getPort();
        InetSocketAddress serverAddr;
        try {
            InetAddress ipAddr = InetAddresses.forString(rawHost);
            serverAddr = new InetSocketAddress(ipAddr, port);
        } catch (IllegalArgumentException e1) {
            LOG.warn("NettyClientConnectionFactory got an unresolved address, addr: {}", rawHost);
            Counter unresolvedDiscoveryHost = SpectatorUtils.newCounter(
                    "unresolvedDiscoveryHost", originName == null ? "unknownOrigin" : originName.getTarget());
            unresolvedDiscoveryHost.increment();
            try {
                serverAddr = new InetSocketAddress(rawHost, port);
            } catch (RuntimeException e2) {
                e1.addSuppressed(e2);
                throw e1;
            }
        }

        return serverAddr;
    }

    /**
     * Given a server chosen from the load balancer, pick the appropriate address to connect to.
     */
    protected SocketAddress pickAddress(DiscoveryResult chosenServer) {
        return pickAddressInternal(chosenServer, connPoolConfig.getOriginName());
    }
}
