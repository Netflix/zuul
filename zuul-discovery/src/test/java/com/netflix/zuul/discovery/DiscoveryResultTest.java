/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiscoveryResultTest {

    @Test
    void hashCodeForNull() {
        DiscoveryResult discoveryResult = new DiscoveryResult(null);
        assertNotNull(discoveryResult.hashCode());
        assertEquals(0, discoveryResult.hashCode());
    }

    @Test
    void serverStatsForEmptySentinel() {
        Truth.assertThat(DiscoveryResult.EMPTY.getServerStats().toString()).isEqualTo("no stats configured for server");
    }

    @Test
    void hostAndPortForNullServer() {
        DiscoveryResult discoveryResult = new DiscoveryResult(null);

        assertEquals("undefined", discoveryResult.getHost());
        assertEquals(-1, discoveryResult.getPort());
    }

    @Test
    void serverStatsCacheForSameServer() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("serverstats-cache")
                .setHostName("serverstats-cache")
                .setPort(7777)
                .build();
        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        DiscoveryEnabledServer serverSecure = new DiscoveryEnabledServer(instanceInfo, true);

        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        DiscoveryResult result1 = new DiscoveryResult(serverSecure, lb.getLoadBalancerStats());

        Truth.assertThat(result.getServerStats()).isSameInstanceAs(result1.getServerStats());
    }

    @Test
    void serverStatsDifferForDifferentServer() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("serverstats-cache")
                .setHostName("serverstats-cache")
                .setPort(7777)
                .build();
        InstanceInfo otherInstance = InstanceInfo.Builder.newBuilder()
                .setAppName("serverstats-cache-2")
                .setHostName("serverstats-cache-2")
                .setPort(7777)
                .build();
        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        DiscoveryEnabledServer serverSecure = new DiscoveryEnabledServer(otherInstance, false);

        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        DiscoveryResult result1 = new DiscoveryResult(serverSecure, lb.getLoadBalancerStats());

        Truth.assertThat(result.getServerStats()).isNotSameInstanceAs(result1.getServerStats());
    }

    @Test
    void ipAddrV4FromInstanceInfo() {
        String ipAddr = "100.1.0.1";
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("ipAddrv4")
                .setHostName("ipAddrv4")
                .setIPAddr(ipAddr)
                .setPort(7777)
                .build();

        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());
        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());

        Truth.assertThat(result.getIPAddr()).isEqualTo(Optional.of(ipAddr));
    }

    @Test
    void ipAddrEmptyForIncompleteInstanceInfo() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("ipAddrMissing")
                .setHostName("ipAddrMissing")
                .setPort(7777)
                .build();

        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());
        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());

        Truth.assertThat(result.getIPAddr()).isEqualTo(Optional.empty());
    }

    @Test
    void sameUnderlyingInstanceInfoEqualsSameResult() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(7777)
                .build();

        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        DiscoveryEnabledServer otherServer = new DiscoveryEnabledServer(instanceInfo, false);

        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        DiscoveryResult otherResult = new DiscoveryResult(otherServer, lb.getLoadBalancerStats());

        Truth.assertThat(result).isEqualTo(otherResult);
    }

    @Test
    void serverInstancesExposingDiffPortsAreNotEqual() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(7777)
                .build();
        InstanceInfo otherPort = InstanceInfo.Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(9999)
                .build();

        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        DiscoveryEnabledServer otherServer = new DiscoveryEnabledServer(otherPort, false);

        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        DiscoveryResult otherResult = new DiscoveryResult(otherServer, lb.getLoadBalancerStats());

        Truth.assertThat(result).isNotEqualTo(otherResult);
    }

    @Test
    void securePortMustCheckInstanceInfo() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("secure-port")
                .setHostName("secure-port")
                .setPort(7777)
                .enablePort(PortType.SECURE, false)
                .build();
        InstanceInfo secureEnabled = InstanceInfo.Builder.newBuilder()
                .setAppName("secure-port")
                .setHostName("secure-port")
                .setPort(7777)
                .enablePort(PortType.SECURE, true)
                .build();

        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, true);
        DiscoveryEnabledServer secureServer = new DiscoveryEnabledServer(secureEnabled, true);
        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        DiscoveryResult secure = new DiscoveryResult(secureServer, lb.getLoadBalancerStats());

        Truth.assertThat(result.isSecurePortEnabled()).isFalse();
        Truth.assertThat(secure.isSecurePortEnabled()).isTrue();
    }
}
