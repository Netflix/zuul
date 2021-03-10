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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.google.common.net.InetAddresses;
import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.discovery.DynamicServerResolver;
import com.netflix.zuul.discovery.NonDiscoveryServer;
import com.netflix.zuul.origins.OriginName;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.DefaultEventLoop;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DefaultClientChannelManager}.  These tests don't use IPv6 addresses because {@link InstanceInfo} is
 * not capable of expressing them.
 */
@RunWith(JUnit4.class)
public class DefaultClientChannelManagerTest {

    @Test
    public void pickAddressInternal_discovery() {
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
    public void pickAddressInternal_discovery_unresolved() {
        InstanceInfo instanceInfo =
                Builder.newBuilder().setAppName("app").setHostName("localhost").setPort(443).build();
        DiscoveryResult s = DiscoveryResult.from(instanceInfo, true);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;

        assertTrue(socketAddress.toString(), socketAddress.getAddress().isLoopbackAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    public void pickAddressInternal_nonDiscovery() {
        NonDiscoveryServer s = new NonDiscoveryServer("192.168.0.1", 443);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;
        assertEquals(InetAddresses.forString("192.168.0.1"), socketAddress.getAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    public void pickAddressInternal_nonDiscovery_unresolved() {
        NonDiscoveryServer s = new NonDiscoveryServer("localhost", 443);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;

        assertTrue(socketAddress.toString(), socketAddress.getAddress().isLoopbackAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    public void updateServerRefOnEmptyDiscoveryResult() {
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
    public void updateServerRefOnValidDiscoveryResult() {
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
}
