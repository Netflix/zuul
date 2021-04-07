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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import java.util.Optional;
import org.junit.Test;

public class DiscoveryResultTest {

    @Test
    public void hashCodeForNull() {
        final DiscoveryResult discoveryResult = new DiscoveryResult(null);
        assertNotNull(discoveryResult.hashCode());
        assertEquals(0, discoveryResult.hashCode());
    }

    @Test
    public void hostAndPortForNullServer() {
        final DiscoveryResult discoveryResult = new DiscoveryResult(null);

        assertEquals("undefined", discoveryResult.getHost());
        assertEquals(-1, discoveryResult.getPort());
    }

    @Test
    public void serverStatsCacheForSameServer() {
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("serverstats-cache")
                .setHostName("serverstats-cache")
                .setPort(7777).build();
        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        final DiscoveryEnabledServer serverSecure = new DiscoveryEnabledServer(instanceInfo, true);

        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        final DiscoveryResult result1 = new DiscoveryResult(serverSecure, lb.getLoadBalancerStats());

        Truth.assertThat(result.getServerStats()).isSameInstanceAs(result1.getServerStats());
    }

    @Test
    public void serverStatsDifferForDifferentServer() {
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("serverstats-cache")
                .setHostName("serverstats-cache")
                .setPort(7777).build();
        final InstanceInfo otherInstance = Builder.newBuilder()
                .setAppName("serverstats-cache-2")
                .setHostName("serverstats-cache-2")
                .setPort(7777).build();
        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        final DiscoveryEnabledServer serverSecure = new DiscoveryEnabledServer(otherInstance, false);

        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        final DiscoveryResult result1 = new DiscoveryResult(serverSecure, lb.getLoadBalancerStats());

        Truth.assertThat(result.getServerStats()).isNotSameInstanceAs(result1.getServerStats());
    }

    @Test
    public void ipAddrV4FromInstanceInfo() {
        final String ipAddr = "100.1.0.1";
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("ipAddrv4")
                .setHostName("ipAddrv4")
                .setIPAddr(ipAddr)
                .setPort(7777).build();

        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());
        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());

        Truth.assertThat(result.getIPAddr()).isEqualTo(Optional.of(ipAddr));
    }

    @Test
    public void ipAddrEmptyForIncompleteInstanceInfo() {
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("ipAddrMissing")
                .setHostName("ipAddrMissing")
                .setPort(7777).build();

        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());
        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());

        Truth.assertThat(result.getIPAddr()).isEqualTo(Optional.empty());
    }

    @Test
    public void sameUnderlyingInstanceInfoEqualsSameResult() {
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(7777).build();

        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        final DiscoveryEnabledServer otherServer = new DiscoveryEnabledServer(instanceInfo, false);

        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        final DiscoveryResult otherResult = new DiscoveryResult(otherServer, lb.getLoadBalancerStats());

        Truth.assertThat(result).isEqualTo(otherResult);
    }

    @Test
    public void serverInstancesExposingDiffPortsAreNotEqual() {
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(7777).build();
        final InstanceInfo otherPort = Builder.newBuilder()
                .setAppName("server-equality")
                .setHostName("server-equality")
                .setPort(9999).build();

        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        final DiscoveryEnabledServer otherServer  = new DiscoveryEnabledServer(otherPort, false);

        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        final DiscoveryResult otherResult = new DiscoveryResult(otherServer, lb.getLoadBalancerStats());

        Truth.assertThat(result).isNotEqualTo(otherResult);
    }

    @Test
    public void securePortMustCheckInstanceInfo() {
        final InstanceInfo instanceInfo = Builder.newBuilder()
                .setAppName("secure-port")
                .setHostName("secure-port")
                .setPort(7777)
                .enablePort(PortType.SECURE, false)
                .build();
        final InstanceInfo secureEnabled = Builder.newBuilder()
                .setAppName("secure-port")
                .setHostName("secure-port")
                .setPort(7777)
                .enablePort(PortType.SECURE, true)
                .build();

        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, true);
        final DiscoveryEnabledServer secureServer = new DiscoveryEnabledServer(secureEnabled, true);
        final DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<>(new DefaultClientConfigImpl());

        final DiscoveryResult result = new DiscoveryResult(server, lb.getLoadBalancerStats());
        final DiscoveryResult secure = new DiscoveryResult(secureServer, lb.getLoadBalancerStats());

        Truth.assertThat(result.isSecurePortEnabled()).isFalse();
        Truth.assertThat(secure.isSecurePortEnabled()).isTrue();
    }
}
