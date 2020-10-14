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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.net.InetAddresses;
import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.zuul.origins.OriginName;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DefaultClientChannelManager}.  These tests don't use IPv6 addresses because {@link InstanceInfo}
 * is not capable of expressing them.
 */
@RunWith(JUnit4.class)
public class DefaultClientChannelManagerTest {

    @Test
    public void deriveInstanceInfoInternal_nonDiscovery() {
        Server s = new Server("localhost", 443);

        InstanceInfo info = DefaultClientChannelManager.deriveInstanceInfoInternal(s);

        // Despite the port and ipaddr being obviously wrong, this is what the previous implementation did.
        // TODO(carl-mastrangelo): find out the original reason why and fix this.
        assertEquals(0, info.getPort());
        assertEquals("localhost", info.getIPAddr());

        assertEquals("localhost:443", info.getId());
        assertEquals("localhost:443", info.getInstanceId());
    }

    @Test
    public void deriveInstanceInfoInternal_discovery() {
        InstanceInfo instanceInfo = Builder.newBuilder().setAppName("app").build();
        Server s = new DiscoveryEnabledServer(instanceInfo, true);

        InstanceInfo actual = DefaultClientChannelManager.deriveInstanceInfoInternal(s);

        assertSame(instanceInfo, actual);
    }
    
    @Test
    public void pickAddressInternal_discovery() {
        InstanceInfo instanceInfo =
                Builder.newBuilder().setAppName("app").setHostName("192.168.0.1").setPort(443).build();
        Server s = new DiscoveryEnabledServer(instanceInfo, true);

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
        Server s = new DiscoveryEnabledServer(instanceInfo, true);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;

        assertTrue(socketAddress.toString(), socketAddress.getAddress().isLoopbackAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    public void pickAddressInternal_nonDiscovery() {
        Server s = new Server("192.168.0.1", 443);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;
        assertEquals(InetAddresses.forString("192.168.0.1"), socketAddress.getAddress());
        assertEquals(443, socketAddress.getPort());
    }

    @Test
    public void pickAddressInternal_nonDiscovery_unresolved() {
        Server s = new Server("localhost", 443);

        SocketAddress addr = DefaultClientChannelManager.pickAddressInternal(s, OriginName.fromVip("vip"));

        Truth.assertThat(addr).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress socketAddress = (InetSocketAddress) addr;

        assertTrue(socketAddress.toString(), socketAddress.getAddress().isLoopbackAddress());
        assertEquals(443, socketAddress.getPort());
    }
}
