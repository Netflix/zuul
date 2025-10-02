/*
 * Copyright 2023 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey.Keys;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.netty.connectionpool.PooledConnection.ConnectionState;
import com.netflix.zuul.netty.server.Server;
import com.netflix.zuul.origins.OriginName;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.concurrent.Promise;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author Justin Guerra
 * @since 2/24/23
 */
class PerServerConnectionPoolTest {

    private static LocalAddress LOCAL_ADDRESS;
    private static MultithreadEventLoopGroup ORIGIN_EVENT_LOOP_GROUP;
    private static MultithreadEventLoopGroup CLIENT_EVENT_LOOP_GROUP;
    private static EventLoop CLIENT_EVENT_LOOP;
    private static Class<? extends Channel> PREVIOUS_CHANNEL_TYPE;

    @Mock
    private ClientChannelManager channelManager;

    private Registry registry;
    private DiscoveryResult discoveryResult;
    private DefaultClientConfigImpl clientConfig;
    private ConnectionPoolConfig connectionPoolConfig;
    private PerServerConnectionPool pool;

    private Counter createNewConnCounter;
    private Counter createConnSucceededCounter;
    private Counter createConnFailedCounter;
    private Counter requestConnCounter;
    private Counter reuseConnCounter;
    private Counter connTakenFromPoolIsNotOpen;
    private Counter closeAboveHighWaterMarkCounter;
    private Counter maxConnsPerHostExceededCounter;
    private Timer connEstablishTimer;
    private AtomicInteger connsInPool;
    private AtomicInteger connsInUse;

    @BeforeAll
    @SuppressWarnings("deprecation")
    static void staticSetup() throws InterruptedException {
        LOCAL_ADDRESS = new LocalAddress(UUID.randomUUID().toString());

        CLIENT_EVENT_LOOP_GROUP = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        CLIENT_EVENT_LOOP = CLIENT_EVENT_LOOP_GROUP.next();

        ORIGIN_EVENT_LOOP_GROUP = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(ORIGIN_EVENT_LOOP_GROUP)
                .localAddress(LOCAL_ADDRESS)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) {}
                });

        bootstrap.bind().sync();
        PREVIOUS_CHANNEL_TYPE = Server.defaultOutboundChannelType.getAndSet(LocalChannel.class);
    }

    @AfterAll
    @SuppressWarnings("deprecation")
    static void staticCleanup() {
        ORIGIN_EVENT_LOOP_GROUP.shutdownGracefully();
        CLIENT_EVENT_LOOP_GROUP.shutdownGracefully();

        if (PREVIOUS_CHANNEL_TYPE != null) {
            Server.defaultOutboundChannelType.set(PREVIOUS_CHANNEL_TYPE);
        }
    }

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        registry = new DefaultRegistry();

        int index = 0;
        createNewConnCounter = registry.counter("fake_counter" + index++);
        createConnSucceededCounter = registry.counter("fake_counter" + index++);
        createConnFailedCounter = registry.counter("fake_counter" + index++);
        requestConnCounter = registry.counter("fake_counter" + index++);
        reuseConnCounter = registry.counter("fake_counter" + index++);
        connTakenFromPoolIsNotOpen = registry.counter("fake_counter" + index++);
        closeAboveHighWaterMarkCounter = registry.counter("fake_counter" + index++);
        maxConnsPerHostExceededCounter = registry.counter("fake_counter" + index++);
        connEstablishTimer = registry.timer("fake_timer");
        connsInPool = new AtomicInteger();
        connsInUse = new AtomicInteger();

        OriginName originName = OriginName.fromVipAndApp("whatever", "whatever-secure");

        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setIPAddr("175.45.176.0")
                .setPort(7001)
                .setAppName("whatever")
                .build();
        discoveryResult = DiscoveryResult.from(instanceInfo, true);

        clientConfig = new DefaultClientConfigImpl();
        connectionPoolConfig = spy(new ConnectionPoolConfigImpl(originName, clientConfig));

        NettyClientConnectionFactory nettyConnectionFactory =
                new NettyClientConnectionFactory(connectionPoolConfig, new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {}
                });

        PooledConnectionFactory pooledConnectionFactory = this::newPooledConnection;

        pool = new PerServerConnectionPool(
                discoveryResult,
                LOCAL_ADDRESS,
                nettyConnectionFactory,
                pooledConnectionFactory,
                connectionPoolConfig,
                clientConfig,
                createNewConnCounter,
                createConnSucceededCounter,
                createConnFailedCounter,
                requestConnCounter,
                reuseConnCounter,
                connTakenFromPoolIsNotOpen,
                closeAboveHighWaterMarkCounter,
                maxConnsPerHostExceededCounter,
                connEstablishTimer,
                connsInPool,
                connsInUse);
    }

    @Test
    void acquireNewConnectionHitsMaxConnections() {
        CurrentPassport currentPassport = CurrentPassport.create();

        clientConfig.set(Keys.MaxConnectionsPerHost, 1);
        discoveryResult.incrementOpenConnectionsCount();

        Promise<PooledConnection> promise = pool.acquire(CLIENT_EVENT_LOOP, currentPassport, new AtomicReference<>());

        assertThat(promise.isSuccess()).isFalse();
        assertThat(promise.cause() instanceof OriginConnectException).isTrue();
        assertThat(maxConnsPerHostExceededCounter.count()).isEqualTo(1);
    }

    @Test
    void acquireNewConnection() throws InterruptedException, ExecutionException {
        CurrentPassport currentPassport = CurrentPassport.create();
        Promise<PooledConnection> promise = pool.acquire(CLIENT_EVENT_LOOP, currentPassport, new AtomicReference<>());

        PooledConnection connection = promise.sync().get();
        assertThat(requestConnCounter.count()).isEqualTo(1);
        assertThat(createNewConnCounter.count()).isEqualTo(1);
        assertThat(currentPassport.findState(PassportState.ORIGIN_CH_CONNECTING))
                .isNotNull();
        assertThat(currentPassport.findState(PassportState.ORIGIN_CH_CONNECTED)).isNotNull();
        assertThat(createConnSucceededCounter.count()).isEqualTo(1);
        assertThat(connsInUse.get()).isEqualTo(1);

        // check state on PooledConnection - not all thread safe
        CLIENT_EVENT_LOOP
                .submit(() -> {
                    checkChannelState(connection, currentPassport, 1);
                })
                .sync();
    }

    @Test
    void acquireConnectionFromPoolAndRelease() throws InterruptedException, ExecutionException {
        CurrentPassport currentPassport = CurrentPassport.create();
        Promise<PooledConnection> promise = pool.acquire(CLIENT_EVENT_LOOP, currentPassport, new AtomicReference<>());

        PooledConnection connection = promise.sync().get();

        CLIENT_EVENT_LOOP
                .submit(() -> {
                    pool.release(connection);
                })
                .sync();

        assertThat(connsInPool.get()).isEqualTo(1);

        CurrentPassport newPassport = CurrentPassport.create();
        Promise<PooledConnection> secondPromise = pool.acquire(CLIENT_EVENT_LOOP, newPassport, new AtomicReference<>());

        PooledConnection connection2 = secondPromise.sync().get();
        assertThat(connection2).isEqualTo(connection);
        assertThat(requestConnCounter.count()).isEqualTo(2);
        assertThat(connsInPool.get()).isEqualTo(0);

        CLIENT_EVENT_LOOP
                .submit(() -> {
                    checkChannelState(connection, newPassport, 2);
                })
                .sync();
    }

    @Test
    void releaseFromPoolButAlreadyClosed() throws InterruptedException, ExecutionException {
        CurrentPassport currentPassport = CurrentPassport.create();
        Promise<PooledConnection> promise = pool.acquire(CLIENT_EVENT_LOOP, currentPassport, new AtomicReference<>());

        PooledConnection connection = promise.sync().get();

        CLIENT_EVENT_LOOP
                .submit(() -> {
                    pool.release(connection);
                })
                .sync();

        // make the connection invalid
        connection.getChannel().deregister().sync();

        CurrentPassport newPassport = CurrentPassport.create();
        Promise<PooledConnection> secondPromise = pool.acquire(CLIENT_EVENT_LOOP, newPassport, new AtomicReference<>());
        PooledConnection connection2 = secondPromise.sync().get();

        assertThat(connection).isNotEqualTo(connection2);
        assertThat(connTakenFromPoolIsNotOpen.count()).isEqualTo(1);
        assertThat(connsInPool.get()).isEqualTo(0);
        assertThat(connection.getChannel().closeFuture().await(5, TimeUnit.SECONDS))
                .as("Channel should have been closed by pool")
                .isTrue();
    }

    @Test
    void releaseFromPoolAboveHighWaterMark() throws InterruptedException, ExecutionException {
        CurrentPassport currentPassport = CurrentPassport.create();

        clientConfig.set(ConnectionPoolConfigImpl.PER_SERVER_WATERLINE, 0);
        Promise<PooledConnection> promise = pool.acquire(CLIENT_EVENT_LOOP, currentPassport, new AtomicReference<>());

        PooledConnection connection = promise.sync().get();
        CLIENT_EVENT_LOOP
                .submit(() -> {
                    assertThat(pool.release(connection)).isFalse();
                    assertThat(closeAboveHighWaterMarkCounter.count()).isEqualTo(1);
                    assertThat(connection.isInPool()).isFalse();
                })
                .sync();
        assertThat(connection.getChannel().closeFuture().await(5, TimeUnit.SECONDS))
                .as("connection should have been closed")
                .isTrue();
    }

    @Test
    void releaseFromPoolWhileDraining() throws InterruptedException, ExecutionException {
        Promise<PooledConnection> promise =
                pool.acquire(CLIENT_EVENT_LOOP, CurrentPassport.create(), new AtomicReference<>());

        PooledConnection connection = promise.sync().get();
        pool.drain();

        CLIENT_EVENT_LOOP
                .submit(() -> {
                    assertThat(connection.isInPool()).isFalse();
                    assertThat(connection.getChannel().isActive())
                            .as("connection was incorrectly closed during the drain")
                            .isTrue();
                    pool.release(connection);
                })
                .sync();

        assertThat(connection.getChannel().closeFuture().await(5, TimeUnit.SECONDS))
                .as("connection should have been closed after release")
                .isTrue();
    }

    @Test
    void acquireWhileDraining() {
        pool.drain();
        assertThat(pool.isAvailable()).isFalse();
        assertThatThrownBy(() -> pool.acquire(CLIENT_EVENT_LOOP, CurrentPassport.create(), new AtomicReference<>()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void gracefulDrain() {
        EmbeddedChannel channel1 = new EmbeddedChannel();
        EmbeddedChannel channel2 = new EmbeddedChannel();

        PooledConnection connection1 = newPooledConnection(channel1);
        PooledConnection connection2 = newPooledConnection(channel2);

        Deque<PooledConnection> connections = pool.getPoolForEventLoop(channel1.eventLoop());
        connections.add(connection1);
        connections.add(connection2);
        connsInPool.set(2);

        assertThat(connsInPool.get()).isEqualTo(2);
        pool.drainIdleConnectionsOnEventLoop(channel1.eventLoop());
        channel1.runPendingTasks();

        assertThat(connsInPool.get()).isEqualTo(0);
        assertThat(connection1.getChannel().closeFuture().isSuccess()).isTrue();
        assertThat(connection2.getChannel().closeFuture().isSuccess()).isTrue();
    }

    @Test
    void handleConnectCompletionWithException() {

        EmbeddedChannel channel = new EmbeddedChannel();
        Promise<PooledConnection> promise = CLIENT_EVENT_LOOP.newPromise();
        pool.handleConnectCompletion(
                channel.newFailedFuture(new RuntimeException("runtime failure")), promise, CurrentPassport.create());

        assertThat(promise.isSuccess()).isFalse();
        assertThat(promise.cause()).isNotNull();
        assertThat(promise.cause()).isInstanceOf(OriginConnectException.class);
        assertThat(promise.cause().getCause()).as("expect cause remains").isInstanceOf(RuntimeException.class);
    }

    @Test
    void handleConnectCompletionWithDecoderExceptionIsUnwrapped() {

        EmbeddedChannel channel = new EmbeddedChannel();
        Promise<PooledConnection> promise = CLIENT_EVENT_LOOP.newPromise();
        pool.handleConnectCompletion(
                channel.newFailedFuture(new DecoderException(new SSLHandshakeException("Invalid tls cert"))),
                promise,
                CurrentPassport.create());

        assertThat(promise.isSuccess()).isFalse();
        assertThat(promise.cause()).isNotNull();
        assertThat(promise.cause()).isInstanceOf(OriginConnectException.class);
        assertThat(promise.cause().getCause())
                .as("expect decoder exception is unwrapped")
                .isInstanceOf(SSLHandshakeException.class);
    }

    private void checkChannelState(PooledConnection connection, CurrentPassport passport, int expectedUsage) {
        Channel channel = connection.getChannel();
        assertThat(connection.getUsageCount()).isEqualTo(expectedUsage);
        assertThat(CurrentPassport.fromChannelOrNull(channel)).isEqualTo(passport);
        assertThat(connection.isReleased()).isFalse();
        assertThat(connection.getConnectionState()).isEqualTo(ConnectionState.WRITE_BUSY);
        assertThat(channel.pipeline().get(DefaultClientChannelManager.IDLE_STATE_HANDLER_NAME))
                .isNull();
    }

    private PooledConnection newPooledConnection(Channel ch) {
        return new PooledConnection(
                ch,
                discoveryResult,
                channelManager,
                registry.counter("fake_close_counter"),
                registry.counter("fake_close_wrt_counter"));
    }
}
