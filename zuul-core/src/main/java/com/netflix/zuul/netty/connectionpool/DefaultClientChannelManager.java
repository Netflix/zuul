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

import com.google.common.collect.Sets;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.insights.PassportStateHttpClientHandler;
import com.netflix.zuul.netty.server.OriginResponseReceiver;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.client.config.CommonClientConfigKey.NFLoadBalancerClassName;

/**
 * User: michaels@netflix.com
 * Date: 7/8/16
 * Time: 12:39 PM
 */
public class DefaultClientChannelManager implements ClientChannelManager {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultClientChannelManager.class);

    public static final String METRIC_PREFIX = "connectionpool";

    private final DynamicServerListLoadBalancer loadBalancer;
    private final ConnectionPoolConfig connPoolConfig;
    private final IClientConfig clientConfig;
    private final Registry spectatorRegistry;

    /* DeploymentContextBasedVIP for which to maintain this connection pool */
    private final String vip;

    private static final Throwable SHUTTING_DOWN_ERR = new IllegalStateException("ConnectionPool is shutting down now.");
    private volatile boolean shuttingDown = false;

    private final Counter createNewConnCounter;
    private final Counter createConnSucceededCounter;
    private final Counter createConnFailedCounter;

    private final Counter closeConnCounter;
    private final Counter requestConnCounter;
    private final Counter reuseConnCounter;
    private final Counter releaseConnCounter;
    private final Counter alreadyClosedCounter;
    private final Counter connTakenFromPoolIsNotOpen;
    private final Counter maxConnsPerHostExceededCounter;
    private final Counter closeWrtBusyConnCounter;
    private final PercentileTimer connEstablishTimer;
    private final AtomicInteger connsInPool;
    private final AtomicInteger connsInUse;

    private final ConcurrentHashMap<Server, PerServerConnectionPool> perServerPools;

    private NettyClientConnectionFactory clientConnFactory;
    private OriginChannelInitializer channelInitializer;

    public static final String IDLE_STATE_HANDLER_NAME = "idleStateHandler";

    public DefaultClientChannelManager(String originName, String vip, IClientConfig clientConfig, Registry spectatorRegistry) {
        this.loadBalancer = createLoadBalancer(clientConfig);

        this.vip = vip;
        this.clientConfig = clientConfig;
        this.spectatorRegistry = spectatorRegistry;
        this.perServerPools = new ConcurrentHashMap<>(200);

        // Setup a listener for Discovery serverlist changes.
        this.loadBalancer.addServerListChangeListener((oldList, newList) -> removeMissingServerConnectionPools(oldList, newList));

        this.connPoolConfig = new ConnectionPoolConfigImpl(originName, this.clientConfig);

        this.createNewConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_create", originName);
        this.createConnSucceededCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_create_success", originName);
        this.createConnFailedCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_create_fail", originName);

        this.closeConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_close", originName);
        this.requestConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_request", originName);
        this.reuseConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_reuse", originName);
        this.releaseConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_release", originName);
        this.alreadyClosedCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_alreadyClosed", originName);
        this.connTakenFromPoolIsNotOpen = SpectatorUtils.newCounter(METRIC_PREFIX + "_fromPoolIsClosed", originName);
        this.maxConnsPerHostExceededCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_maxConnsPerHostExceeded", originName);
        this.closeWrtBusyConnCounter = SpectatorUtils.newCounter(METRIC_PREFIX + "_closeWrtBusyConnCounter", originName);
        this.connEstablishTimer = PercentileTimer.get(spectatorRegistry, spectatorRegistry.createId(METRIC_PREFIX + "_createTiming", "id", originName));
        this.connsInPool = SpectatorUtils.newGauge(METRIC_PREFIX + "_inPool", originName, new AtomicInteger());
        this.connsInUse = SpectatorUtils.newGauge(METRIC_PREFIX + "_inUse", originName, new AtomicInteger());
    }

    @Override
    public void init()
    {
        // Load channel initializer and conn factory.
        // We don't do this within the constructor because some subclass may not be initialized until post-construct.
        this.channelInitializer = createChannelInitializer(clientConfig, connPoolConfig, spectatorRegistry);
        this.clientConnFactory = createNettyClientConnectionFactory(connPoolConfig, channelInitializer);
    }

    protected OriginChannelInitializer createChannelInitializer(IClientConfig clientConfig, ConnectionPoolConfig connPoolConfig, Registry registry) {
        return new DefaultOriginChannelInitializer(connPoolConfig, registry);
    }

    protected NettyClientConnectionFactory createNettyClientConnectionFactory(ConnectionPoolConfig connPoolConfig,
                                                                              ChannelInitializer<? extends Channel> clientConnInitializer) {
        return new NettyClientConnectionFactory(connPoolConfig, clientConnInitializer);
    }

    protected DynamicServerListLoadBalancer createLoadBalancer(IClientConfig clientConfig) {
        // Create and configure a loadbalancer for this vip.
        String defaultLoadBalancerClassName = getLoadBalancerClass().getName();
        String loadBalancerClassName = clientConfig.get(NFLoadBalancerClassName, defaultLoadBalancerClassName);

        DynamicServerListLoadBalancer lb;
        try {
            Class clazz = Class.forName(loadBalancerClassName);
            lb = (DynamicServerListLoadBalancer) clazz.newInstance();
            lb.initWithNiwsConfig(clientConfig);
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not instantiate requested class for LoadBalancer! " +
                    "loadBalancerClassNam=" + String.valueOf(loadBalancerClassName), e);
        }

        return lb;
    }

    protected Class<? extends DynamicServerListLoadBalancer> getLoadBalancerClass() {
        return ZoneAwareLoadBalancer.class;
    }

    protected void removeMissingServerConnectionPools(List<Server> oldList, List<Server> newList) {
        Set<Server> oldSet = new HashSet<>(oldList);
        Set<Server> newSet = new HashSet<>(newList);
        Set<Server> removedSet = Sets.difference(oldSet, newSet);

        if (!removedSet.isEmpty()) {
            LOG.debug("Removing connection pools for missing servers. vip = " + this.vip
                    + ". " + removedSet.size() + " servers gone.");

            for (Server s : removedSet) {
                PerServerConnectionPool pool = perServerPools.remove(s);
                if (pool != null) {
                    pool.shutdown();
                }
            }
        }
    }

    @Override
    public ConnectionPoolConfig getConfig() {
        return connPoolConfig;
    }

    @Override
    public boolean isAvailable() {
        return !loadBalancer.getReachableServers().isEmpty();
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

        loadBalancer.shutdown();

        for (PerServerConnectionPool pool : perServerPools.values()) {
            pool.shutdown();
        }
    }

    @Override
    public boolean release(final PooledConnection conn) {

        conn.stopRequestTimer();
        releaseConnCounter.increment();
        connsInUse.decrementAndGet();

        final ServerStats stats = conn.getServerStats();
        stats.decrementActiveRequestsCount();
        stats.incrementNumRequests();

        if (shuttingDown) {
            return false;
        }

        boolean released = false;

        if (conn.isShouldClose() ||
                // if the connection has been around too long (i.e. too many requests), then close it
                conn.getUsageCount() > connPoolConfig.getMaxRequestsPerConnection()) {

            // Close and discard the connection, as it has been flagged (possibly due to receiving a non-channel error like a 503).
            conn.setInPool(false);
            conn.close();
        }
        else if (stats.isCircuitBreakerTripped()) {
            // Don't put conns for currently circuit-tripped servers back into the pool.
            conn.setInPool(false);
            conn.close();
        }
        else if (!conn.isActive()) {
            // Connection is already closed, so discard.
            alreadyClosedCounter.increment();
            // make sure to decrement OpenConnectionCounts
            conn.updateServerStats();
            conn.setInPool(false);
        }
        else {
            final ChannelPipeline pipeline = conn.getChannel().pipeline();
            removeHandlerFromPipeline(OriginResponseReceiver.CHANNEL_HANDLER_NAME, pipeline);
            pipeline.addAfter(PassportStateHttpClientHandler.PASSPORT_STATE_HTTP_CLIENT_HANDLER_NAME, IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(0, 0, connPoolConfig.getIdleTimeout(), TimeUnit.MILLISECONDS));


            // Attempt to return connection to the pool.
            PerServerConnectionPool pool = perServerPools.get(conn.getServer());
            if (pool != null) {
                released = pool.release(conn);
            }
            else {
                // The pool for this server no longer exists (maybe due to it failling out of
                // discovery).
                conn.setInPool(false);
                released = false;
                conn.close();
            }

            if (LOG.isDebugEnabled()) LOG.debug("PooledConnection released: " + conn.toString());
        }

        return released;
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
        PerServerConnectionPool pool = perServerPools.get(conn.getServer());
        if (pool != null) {
            return pool.remove(conn);
        }
        else {
            // The pool for this server no longer exists (maybe due to it failling out of
            // discovery).
            conn.setInPool(false);
            connsInPool.decrementAndGet();
            return false;
        }
    }

    @Override
    public Promise<PooledConnection> acquire(final EventLoop eventLoop) {
        return acquire(eventLoop, null, null, null, 1, CurrentPassport.create(), new AtomicReference<>());
    }

    @Override
    public Promise<PooledConnection> acquire(final EventLoop eventLoop, final Object key, final String httpMethod,
                                             final String uri, final int attemptNum, final CurrentPassport passport,
                                             final AtomicReference<Server> selectedServer) {

        if (attemptNum < 1) {
            throw new IllegalArgumentException("attemptNum must be greater than zero");
        }

        if (shuttingDown) {
            Promise<PooledConnection> promise = eventLoop.newPromise();
            promise.setFailure(SHUTTING_DOWN_ERR);
            return promise;
        }

        // Choose the next load-balanced server.
        final Server chosenServer = loadBalancer.chooseServer(key);
        if (chosenServer == null) {
            Promise<PooledConnection> promise = eventLoop.newPromise();
            promise.setFailure(new OriginConnectException("No servers available", OutboundErrorType.NO_AVAILABLE_SERVERS));
            return promise;
        }

        final InstanceInfo instanceInfo = chosenServer instanceof DiscoveryEnabledServer ?
                ((DiscoveryEnabledServer) chosenServer).getInstanceInfo() :
                // create mock instance info for non-discovery instances
                new InstanceInfo(chosenServer.getId(), null, null, chosenServer.getHost(), chosenServer.getId(),
                        null, null, null, null, null, null, null, null, 0, null, null, null, null, null, null, null, null, null, null, null);


        selectedServer.set(chosenServer);

        // Now get the connection-pool for this server.
        PerServerConnectionPool pool = perServerPools.computeIfAbsent(chosenServer, s -> {

            // Get the stats from LB for this server.
            LoadBalancerStats lbStats = loadBalancer.getLoadBalancerStats();
            ServerStats stats = lbStats.getSingleServerStat(chosenServer);

            final ClientChannelManager clientChannelMgr = this;
            PooledConnectionFactory pcf = ch -> new PooledConnection(ch, chosenServer, clientChannelMgr,
                    instanceInfo, stats, closeConnCounter, closeWrtBusyConnCounter);

            // Create a new pool for this server.
            return new PerServerConnectionPool(
                    chosenServer,
                    stats,
                    instanceInfo,
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
                    maxConnsPerHostExceededCounter,
                    connEstablishTimer,
                    connsInPool,
                    connsInUse
            );
        });

        return pool.acquire(eventLoop, null, httpMethod, uri, attemptNum, passport);
    }

    @Override
    public int getConnsInPool() {
        return connsInPool.get();
    }

    @Override
    public int getConnsInUse() {
        return connsInUse.get();
    }

    // This is just used for information in the RestClient 'bridge'.
    public DynamicServerListLoadBalancer getLoadBalancer() {
        return this.loadBalancer;
    }

    public IClientConfig getClientConfig() {
        return this.loadBalancer.getClientConfig();
    }

    protected ConcurrentHashMap<Server, PerServerConnectionPool> getPerServerPools() {
        return perServerPools;
    }
}
