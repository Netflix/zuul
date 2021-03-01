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

import com.netflix.client.config.IClientConfig;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Timer;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 7/8/16
 * Time: 1:09 PM
 */
public class PerServerConnectionPool implements IConnectionPool
{
    private static final Logger LOG = LoggerFactory.getLogger(PerServerConnectionPool.class);

    private final ConcurrentHashMap<EventLoop, Deque<PooledConnection>> connectionsPerEventLoop =
            new ConcurrentHashMap<>();

    private final DiscoveryResult server;
    private final SocketAddress serverAddr;
    private final NettyClientConnectionFactory connectionFactory;
    private final PooledConnectionFactory pooledConnectionFactory;
    private final ConnectionPoolConfig config;
    private final IClientConfig niwsClientConfig;

    private final Counter createNewConnCounter;
    private final Counter createConnSucceededCounter;
    private final Counter createConnFailedCounter;
    
    private final Counter requestConnCounter;
    private final Counter reuseConnCounter;
    private final Counter connTakenFromPoolIsNotOpen;
    private final Counter maxConnsPerHostExceededCounter;
    private final Timer connEstablishTimer;
    private final AtomicInteger connsInPool;
    private final AtomicInteger connsInUse;

    /** 
     * This is the count of connections currently in progress of being established. 
     * They will only be added to connsInUse _after_ establishment has completed.
     */
    private final AtomicInteger connCreationsInProgress;

    public PerServerConnectionPool(
            DiscoveryResult server,
            SocketAddress serverAddr,
            NettyClientConnectionFactory connectionFactory,
            PooledConnectionFactory pooledConnectionFactory,
            ConnectionPoolConfig config,
            IClientConfig niwsClientConfig,
            Counter createNewConnCounter,
            Counter createConnSucceededCounter,
            Counter createConnFailedCounter,
            Counter requestConnCounter, Counter reuseConnCounter,
            Counter connTakenFromPoolIsNotOpen,
            Counter maxConnsPerHostExceededCounter,
            Timer connEstablishTimer,
            AtomicInteger connsInPool,
            AtomicInteger connsInUse) {
        this.server = server;
        // Note: child classes can sometimes connect to different addresses than
        this.serverAddr = Objects.requireNonNull(serverAddr, "serverAddr");
        this.connectionFactory = connectionFactory;
        this.pooledConnectionFactory = pooledConnectionFactory;
        this.config = config;
        this.niwsClientConfig = niwsClientConfig;
        this.createNewConnCounter = createNewConnCounter;
        this.createConnSucceededCounter = createConnSucceededCounter;
        this.createConnFailedCounter = createConnFailedCounter;
        this.requestConnCounter = requestConnCounter;
        this.reuseConnCounter = reuseConnCounter;
        this.connTakenFromPoolIsNotOpen = connTakenFromPoolIsNotOpen;
        this.maxConnsPerHostExceededCounter = maxConnsPerHostExceededCounter;
        this.connEstablishTimer = connEstablishTimer;
        this.connsInPool = connsInPool;
        this.connsInUse = connsInUse;
        
        this.connCreationsInProgress = new AtomicInteger(0);
    }

    @Override
    public ConnectionPoolConfig getConfig()
    {
        return this.config;
    }

    public IClientConfig getNiwsClientConfig()
    {
        return niwsClientConfig;
    }

    @Override
    public boolean isAvailable()
    {
        return true;
    }

    /** function to run when a connection is acquired before returning it to caller. */
    private void onAcquire(final PooledConnection conn, CurrentPassport passport)
    {
        passport.setOnChannel(conn.getChannel());
        removeIdleStateHandler(conn);

        conn.setInUse();
        if (LOG.isDebugEnabled()) LOG.debug("PooledConnection acquired: " + conn.toString());
    }

    protected void removeIdleStateHandler(PooledConnection conn) {
        DefaultClientChannelManager.removeHandlerFromPipeline(DefaultClientChannelManager.IDLE_STATE_HANDLER_NAME, conn.getChannel().pipeline());
    }

    @Override
    public Promise<PooledConnection> acquire(
            EventLoop eventLoop, CurrentPassport passport, AtomicReference<? super InetAddress> selectedHostAddr) {
        requestConnCounter.increment();
        server.incrementActiveRequestsCount();
        
        Promise<PooledConnection> promise = eventLoop.newPromise();

        // Try getting a connection from the pool.
        final PooledConnection conn = tryGettingFromConnectionPool(eventLoop);
        if (conn != null) {
            // There was a pooled connection available, so use this one.
            conn.startRequestTimer();
            conn.incrementUsageCount();
            conn.getChannel().read();
            onAcquire(conn, passport);
            initPooledConnection(conn, promise);
            selectedHostAddr.set(getSelectedHostString(serverAddr));
        }
        else {
            // connection pool empty, create new connection using client connection factory.
            tryMakingNewConnection(eventLoop, promise, passport, selectedHostAddr);
        }

        return promise;
    }

    public PooledConnection tryGettingFromConnectionPool(EventLoop eventLoop)
    {
        PooledConnection conn;
        Deque<PooledConnection> connections = getPoolForEventLoop(eventLoop);
        while ((conn = connections.poll()) != null) {

            conn.setInPool(false);

            /* Check that the connection is still open. */
            if (isValidFromPool(conn)) {
                reuseConnCounter.increment();
                connsInUse.incrementAndGet();
                connsInPool.decrementAndGet();
                return conn;
            }
            else {
                connTakenFromPoolIsNotOpen.increment();
                connsInPool.decrementAndGet();
                conn.close();
            }
        }
        return null;
    }

    protected boolean isValidFromPool(PooledConnection conn) {
        return conn.isActive() && conn.getChannel().isOpen();
    }

    protected void initPooledConnection(PooledConnection conn, Promise<PooledConnection> promise) {
        // add custom init code by overriding this method
        promise.setSuccess(conn);
    }

    protected Deque<PooledConnection> getPoolForEventLoop(EventLoop eventLoop)
    {
        // We don't want to block under any circumstances, so can't use CHM.computeIfAbsent().
        // Instead we accept the slight inefficiency of an unnecessary instantiation of a ConcurrentLinkedDeque.

        Deque<PooledConnection> pool = connectionsPerEventLoop.get(eventLoop);
        if (pool == null) {
            pool = new ConcurrentLinkedDeque<>();
            connectionsPerEventLoop.putIfAbsent(eventLoop, pool);
        }
        return pool;
    }

    protected void tryMakingNewConnection(
            EventLoop eventLoop, Promise<PooledConnection> promise, CurrentPassport passport,
            AtomicReference<? super InetAddress> selectedHostAddr) {
        // Enforce MaxConnectionsPerHost config.
        int maxConnectionsPerHost = config.maxConnectionsPerHost();
        int openAndOpeningConnectionCount = server.getOpenConnectionsCount() + connCreationsInProgress.get();
        if (maxConnectionsPerHost != -1 && openAndOpeningConnectionCount >= maxConnectionsPerHost) {
            maxConnsPerHostExceededCounter.increment();
            promise.setFailure(new OriginConnectException(
                "maxConnectionsPerHost=" + maxConnectionsPerHost + ", connectionsPerHost=" + openAndOpeningConnectionCount,
                    OutboundErrorType.ORIGIN_SERVER_MAX_CONNS));
            LOG.warn("Unable to create new connection because at MaxConnectionsPerHost! "
                            + "maxConnectionsPerHost=" + maxConnectionsPerHost
                            + ", connectionsPerHost=" + openAndOpeningConnectionCount
                            + ", host=" + server.getServerId()
                            + "origin=" + config.getOriginName()
                    );
            return;
        }

        try {
            createNewConnCounter.increment();
            connCreationsInProgress.incrementAndGet();
            passport.add(PassportState.ORIGIN_CH_CONNECTING);

            selectedHostAddr.set(getSelectedHostString(serverAddr));

            final ChannelFuture cf = connectToServer(eventLoop, passport, serverAddr);

            if (cf.isDone()) {
                handleConnectCompletion(cf, promise, passport);
            }
            else {
                cf.addListener(future -> {
                    try {
                        handleConnectCompletion((ChannelFuture) future, promise, passport);
                    }
                    catch (Throwable e) {
                        if (! promise.isDone()) {
                            promise.setFailure(e);
                        }
                        LOG.warn("Error creating new connection! "
                                        + "origin=" + config.getOriginName()
                                        + ", host=" + server.getServerId()
                                );
                    }
                });
            }
        }
        catch (Throwable e) {
            promise.setFailure(e);
        }
    }

    protected ChannelFuture connectToServer(EventLoop eventLoop, CurrentPassport passport, SocketAddress serverAddr) {
        return connectionFactory.connect(eventLoop, serverAddr, passport);
    }

    protected void handleConnectCompletion(
            ChannelFuture cf, Promise<PooledConnection> callerPromise, CurrentPassport passport) {
        connCreationsInProgress.decrementAndGet();
        
        if (cf.isSuccess()) {
            
            passport.add(PassportState.ORIGIN_CH_CONNECTED);
            
            server.incrementOpenConnectionsCount();
            createConnSucceededCounter.increment();
            connsInUse.incrementAndGet();

            createConnection(cf, callerPromise, passport);
        }
        else {
            server.incrementSuccessiveConnectionFailureCount();
            server.addToFailureCount();
            server.decrementActiveRequestsCount();
            createConnFailedCounter.increment();
            callerPromise.setFailure(new OriginConnectException(cf.cause().getMessage(), OutboundErrorType.CONNECT_ERROR));
        }
    }

    protected void createConnection(
            ChannelFuture cf, Promise<PooledConnection> callerPromise, CurrentPassport passport) {
        final PooledConnection conn = pooledConnectionFactory.create(cf.channel());

        conn.incrementUsageCount();
        conn.startRequestTimer();
        conn.getChannel().read();
        onAcquire(conn, passport);
        callerPromise.setSuccess(conn);
    }

    @Override
    public boolean release(PooledConnection conn)
    {
        if (conn == null) {
            return false;
        }
        if (conn.isInPool()) {
            return false;
        }

        // Get the eventloop for this channel.
        EventLoop eventLoop = conn.getChannel().eventLoop();
        Deque<PooledConnection> connections = getPoolForEventLoop(eventLoop);
        
        CurrentPassport passport = CurrentPassport.fromChannel(conn.getChannel());

        // Discard conn if already at least above waterline in the pool already for this server.
        int poolWaterline = config.perServerWaterline();
        if (poolWaterline > -1 && connections.size() >= poolWaterline) {
            conn.close();
            conn.setInPool(false);
            return false;
        }
        // Attempt to return connection to the pool.
        else if (connections.offer(conn)) {
            conn.setInPool(true);
            connsInPool.incrementAndGet();
            passport.add(PassportState.ORIGIN_CH_POOL_RETURNED);
            return true;
        }
        else {
            // If the pool is full, then close the conn and discard.
            conn.close();
            conn.setInPool(false);
            return false;
        }
    }

    @Override
    public boolean remove(PooledConnection conn)
    {
        if (conn == null) {
            return false;
        }
        if (! conn.isInPool()) {
            return false;
        }

        // Get the eventloop for this channel.
        EventLoop eventLoop = conn.getChannel().eventLoop();

        // Attempt to return connection to the pool.
        Deque<PooledConnection> connections = getPoolForEventLoop(eventLoop);
        if (connections.remove(conn)) {
            conn.setInPool(false);
            connsInPool.decrementAndGet();
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void shutdown()
    {
        for (Deque<PooledConnection> connections : connectionsPerEventLoop.values()) {
            for (PooledConnection conn : connections) {
                conn.close();
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

    @Nullable
    private static InetAddress getSelectedHostString(SocketAddress addr) {
        if (addr instanceof InetSocketAddress) {
            return ((InetSocketAddress) addr).getAddress();
        } else {
            // If it's some other kind of address, just set it to empty
            return null;
        }
    }

}
