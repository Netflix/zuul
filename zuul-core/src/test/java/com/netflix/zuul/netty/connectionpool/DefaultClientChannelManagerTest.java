/*
 * Copyright 2020 Netflix, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.InetAddresses;
import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.discovery.DynamicServerResolver;
import com.netflix.zuul.discovery.NonDiscoveryServer;
import com.netflix.zuul.netty.server.Server;
import com.netflix.zuul.origins.OriginName;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link DefaultClientChannelManager}.  These tests don't use IPv6 addresses because {@link InstanceInfo} is
 * not capable of expressing them.
 */
class DefaultClientChannelManagerTest {

    @Test
    void pickAddressInternal_discovery() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("app")
                .setHostName("192.168.0.1")
                .setPort(443)
                .build();
        DiscoveryResult s = DiscoveryResult.from(instanceInfo, true);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;
        assertEquals(InetAddresses.forString("192.168.0.1"), socketAddress.getAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    void pickAddressInternal_discovery_unresolved() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("app")
                .setHostName("localhost")
                .setPort(443)
                .build();
        DiscoveryResult s = DiscoveryResult.from(instanceInfo, true);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;

        assertTrue(socketAddress.getAddress().isLoopbackAddress(), socketAddress.toString());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    void pickAddressInternal_nonDiscovery() {
        NonDiscoveryServer s = new NonDiscoveryServer("192.168.0.1", 443);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;
        assertEquals(InetAddresses.forString("192.168.0.1"), socketAddress.getAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    void pickAddressInternal_nonDiscovery_unresolved() {
        NonDiscoveryServer s = new NonDiscoveryServer("localhost", 443);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;

        assertTrue(socketAddress.getAddress().isLoopbackAddress(), socketAddress.toString());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    void updateServerRefOnEmptyDiscoveryResult() {
        OriginName originName = OriginName.fromVip("vip", "test");
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        DynamicServerResolver resolver = mock(DynamicServerResolver.class);

        when(resolver.resolve(any())).thenReturn(DiscoveryResult.EMPTY);

        DefaultClientChannelManager clientChannelManager =
                new DefaultClientChannelManager(originName, clientConfig, resolver, new DefaultRegistry());

        AtomicReference<DiscoveryResult> serverRef = new AtomicReference<>();

        Promise<PooledConnection> promise = clientChannelManager.acquire(
                new DefaultEventLoop(), null, CurrentPassport.create(), serverRef, new AtomicReference<>());

        Truth.assertThat(promise.isSuccess()).isFalse();
        Truth.assertThat(serverRef.get()).isSameInstanceAs(DiscoveryResult.EMPTY);
    }

    @Test
    void updateServerRefOnValidDiscoveryResult() throws InterruptedException {
        OriginName originName = OriginName.fromVip("vip", "test");
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();

        DynamicServerResolver resolver = mock(DynamicServerResolver.class);
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(7777)
                .build();
        DiscoveryResult discoveryResult = DiscoveryResult.from(instanceInfo, false);

        when(resolver.resolve(any())).thenReturn(discoveryResult);

        DefaultClientChannelManager clientChannelManager =
                new DefaultClientChannelManager(originName, clientConfig, resolver, new DefaultRegistry());

        AtomicReference<DiscoveryResult> serverRef = new AtomicReference<>();

        // TODO(argha-c) capture and assert on the promise once we have a dummy with ServerStats initialized
        var unusedFuture = clientChannelManager.acquire(
                new DefaultEventLoop(), null, CurrentPassport.create(), serverRef, new AtomicReference<>());

        Truth.assertThat(serverRef.get()).isSameInstanceAs(discoveryResult);
    }

    @Test
    void initializeAndShutdown() throws Exception {
        String appName = "app-" + UUID.randomUUID();
        ServerSocket serverSocket = new ServerSocket(0);
        InetSocketAddress serverSocketAddress = (InetSocketAddress) serverSocket.getLocalSocketAddress();
        String serverHostname = serverSocketAddress.getHostName();
        int serverPort = serverSocketAddress.getPort();
        OriginName originName = OriginName.fromVipAndApp("vip", appName);
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();

        Server.defaultOutboundChannelType.set(NioSocketChannel.class);

        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName(appName)
                .setHostName(serverHostname)
                .setPort(serverPort)
                .build();
        DiscoveryResult discoveryResult = DiscoveryResult.from(instanceInfo, true);

        DynamicServerResolver resolver = mock(DynamicServerResolver.class);
        when(resolver.resolve(any())).thenReturn(discoveryResult);
        when(resolver.hasServers()).thenReturn(true);

        Registry registry = new DefaultRegistry();
        DefaultClientChannelManager clientChannelManager =
                new DefaultClientChannelManager(originName, clientConfig, resolver, registry);

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(10);
        EventLoop eventLoop = eventLoopGroup.next();

        clientChannelManager.init();

        Truth.assertThat(clientChannelManager.getConnsInUse()).isEqualTo(0);

        Promise<PooledConnection> promiseConn = clientChannelManager.acquire(eventLoop);
        promiseConn.await(200, TimeUnit.MILLISECONDS);
        assertTrue(promiseConn.isDone());
        assertTrue(promiseConn.isSuccess());

        PooledConnection connection = promiseConn.get();
        assertTrue(connection.isActive());
        assertFalse(connection.isInPool());

        Truth.assertThat(clientChannelManager.getConnsInUse()).isEqualTo(1);

        boolean releaseResult = clientChannelManager.release(connection);
        assertTrue(releaseResult);
        assertTrue(connection.isInPool());

        Truth.assertThat(clientChannelManager.getConnsInUse()).isEqualTo(0);

        clientChannelManager.shutdown();
        serverSocket.close();
    }

    @Test
    void closeOnCircuitBreaker() {
        OriginName originName = OriginName.fromVipAndApp("whatever", "whatever");
        DefaultClientChannelManager manager =
                new DefaultClientChannelManager(
                        originName,
                        new DefaultClientConfigImpl(),
                        Mockito.mock(DynamicServerResolver.class),
                        new NoopRegistry()) {
                    @Override
                    protected void updateServerStatsOnRelease(PooledConnection conn) {}
                };

        PooledConnection connection = mock(PooledConnection.class);
        DiscoveryResult discoveryResult = mock(DiscoveryResult.class);
        doReturn(discoveryResult).when(connection).getServer();
        doReturn(true).when(discoveryResult).isCircuitBreakerTripped();
        doReturn(new EmbeddedChannel()).when(connection).getChannel();

        Truth.assertThat(manager.release(connection)).isFalse();
        verify(connection).setInPool(false);
        verify(connection).close();
    }

    @Test
    void skipCloseOnCircuitBreaker() {
        OriginName originName = OriginName.fromVipAndApp("whatever", "whatever");
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        DefaultClientChannelManager manager =
                new DefaultClientChannelManager(
                        originName, clientConfig, Mockito.mock(DynamicServerResolver.class), new NoopRegistry()) {
                    @Override
                    protected void updateServerStatsOnRelease(PooledConnection conn) {}

                    @Override
                    protected void releaseHandlers(PooledConnection conn) {}
                };

        PooledConnection connection = mock(PooledConnection.class);
        DiscoveryResult discoveryResult = mock(DiscoveryResult.class);
        doReturn(discoveryResult).when(connection).getServer();
        doReturn(true).when(discoveryResult).isCircuitBreakerTripped();
        doReturn(true).when(connection).isActive();
        doReturn(new EmbeddedChannel()).when(connection).getChannel();

        IConnectionPool connectionPool = mock(IConnectionPool.class);
        doReturn(true).when(connectionPool).release(connection);
        manager.getPerServerPools().put(discoveryResult, connectionPool);
        clientConfig.set(ConnectionPoolConfigImpl.CLOSE_ON_CIRCUIT_BREAKER, false);

        Truth.assertThat(manager.release(connection)).isTrue();
        verify(connection, never()).setInPool(anyBoolean());
        verify(connection, never()).close();
    }
}
