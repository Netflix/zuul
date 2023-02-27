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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.google.common.net.InetAddresses;
import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
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

/**
 * Tests for {@link DefaultClientChannelManager}.  These tests don't use IPv6 addresses because {@link InstanceInfo} is
 * not capable of expressing them.
 */
class DefaultClientChannelManagerTest {

    @Test
    void pickAddressInternal_discovery() {
        InstanceInfo instanceInfo =
                Builder.newBuilder().setAppName("app").setHostName("192.168.0.1").setPort(443).build();
        DiscoveryResult s = DiscoveryResult.from(instanceInfo, true);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;
        assertEquals(InetAddresses.forString("192.168.0.1"), socketAddress.getAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    void pickAddressInternal_discovery_unresolved() {
        InstanceInfo instanceInfo =
                Builder.newBuilder().setAppName("app").setHostName("localhost").setPort(443).build();
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
        final DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        final DynamicServerResolver resolver = mock(DynamicServerResolver.class);

        when(resolver.resolve(any())).thenReturn(DiscoveryResult.EMPTY);

        final DefaultClientChannelManager clientChannelManager = new DefaultClientChannelManager(originName,
                clientConfig, resolver, new DefaultRegistry());

        final AtomicReference<DiscoveryResult> serverRef = new AtomicReference<>();

        final Promise<PooledConnection> promise = clientChannelManager
                .acquire(new DefaultEventLoop(), null, CurrentPassport.create(), serverRef, new AtomicReference<>());

        Truth.assertThat(promise.isSuccess()).isFalse();
        Truth.assertThat(serverRef.get()).isSameInstanceAs(DiscoveryResult.EMPTY);
    }

    @Test
    void updateServerRefOnValidDiscoveryResult() {
        OriginName originName = OriginName.fromVip("vip", "test");
        final DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();

        final DynamicServerResolver resolver = mock(DynamicServerResolver.class);
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(7777).build();
        final DiscoveryResult discoveryResult = DiscoveryResult.from(instanceInfo, false);

        when(resolver.resolve(any())).thenReturn(discoveryResult);

        final DefaultClientChannelManager clientChannelManager = new DefaultClientChannelManager(originName,
                clientConfig, resolver, new DefaultRegistry());

        final AtomicReference<DiscoveryResult> serverRef = new AtomicReference<>();

        //TODO(argha-c) capture and assert on the promise once we have a dummy with ServerStats initialized
        clientChannelManager
                .acquire(new DefaultEventLoop(), null, CurrentPassport.create(), serverRef, new AtomicReference<>());

        Truth.assertThat(serverRef.get()).isSameInstanceAs(discoveryResult);
    }

    @Test
    void initializeAndShutdown() throws Exception {
        final String appName = "app-" + UUID.randomUUID();
        final ServerSocket serverSocket = new ServerSocket(0);
        final InetSocketAddress serverSocketAddress = (InetSocketAddress) serverSocket.getLocalSocketAddress();
        final String serverHostname = serverSocketAddress.getHostName();
        final int serverPort = serverSocketAddress.getPort();
        final OriginName originName = OriginName.fromVipAndApp("vip", appName);
        final DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();

        Server.defaultOutboundChannelType.set(NioSocketChannel.class);

        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName(appName)
                .setHostName(serverHostname)
                .setPort(serverPort)
                .build();
        DiscoveryResult discoveryResult = DiscoveryResult.from(instanceInfo, true);

        final DynamicServerResolver resolver = mock(DynamicServerResolver.class);
        when(resolver.resolve(any())).thenReturn(discoveryResult);
        when(resolver.hasServers()).thenReturn(true);

        final Registry registry = new DefaultRegistry();
        final DefaultClientChannelManager clientChannelManager = new DefaultClientChannelManager(originName,
                clientConfig, resolver, registry);

        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(10);
        final EventLoop eventLoop = eventLoopGroup.next();

        clientChannelManager.init();

        Truth.assertThat(clientChannelManager.getConnsInUse()).isEqualTo(0);

        final Promise<PooledConnection> promiseConn = clientChannelManager.acquire(eventLoop);
        promiseConn.await(200, TimeUnit.MILLISECONDS);
        assertTrue(promiseConn.isDone());
        assertTrue(promiseConn.isSuccess());

        final PooledConnection connection = promiseConn.get();
        assertTrue(connection.isActive());
        assertFalse(connection.isInPool());

        Truth.assertThat(clientChannelManager.getConnsInUse()).isEqualTo(1);

        final boolean releaseResult = clientChannelManager.release(connection);
        assertTrue(releaseResult);
        assertTrue(connection.isInPool());

        Truth.assertThat(clientChannelManager.getConnsInUse()).isEqualTo(0);

        clientChannelManager.shutdown();
        serverSocket.close();
    }
}
