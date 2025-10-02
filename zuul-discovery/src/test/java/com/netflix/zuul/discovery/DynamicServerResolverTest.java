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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.zuul.resolver.ResolverListener;
import java.util.List;
import org.junit.jupiter.api.Test;

class DynamicServerResolverTest {

    @Test
    void verifyListenerUpdates() {

        class CustomListener implements ResolverListener<DiscoveryResult> {

            private List<DiscoveryResult> resultSet = Lists.newArrayList();

            @Override
            public void onChange(List<DiscoveryResult> changedSet) {
                resultSet = changedSet;
            }

            public List<DiscoveryResult> updatedList() {
                return resultSet;
            }
        }

        CustomListener listener = new CustomListener();
        DynamicServerResolver resolver = new DynamicServerResolver(new DefaultClientConfigImpl());
        resolver.setListener(listener);

        InstanceInfo first = InstanceInfo.Builder.newBuilder()
                .setAppName("zuul-discovery-1")
                .setHostName("zuul-discovery-1")
                .setIPAddr("100.10.10.1")
                .setPort(443)
                .build();
        InstanceInfo second = InstanceInfo.Builder.newBuilder()
                .setAppName("zuul-discovery-2")
                .setHostName("zuul-discovery-2")
                .setIPAddr("100.10.10.2")
                .setPort(443)
                .build();
        DiscoveryEnabledServer server1 = new DiscoveryEnabledServer(first, true);
        DiscoveryEnabledServer server2 = new DiscoveryEnabledServer(second, true);

        resolver.onUpdate(ImmutableList.of(server1, server2), ImmutableList.of());

        assertThat(listener.updatedList()).containsExactly(new DiscoveryResult(server1), new DiscoveryResult(server2));
    }

    @Test
    void properSentinelValueWhenServersUnavailable() {
        DynamicServerResolver resolver = new DynamicServerResolver(new DefaultClientConfigImpl());

        DiscoveryResult nonExistentServer = resolver.resolve(null);

        assertThat(nonExistentServer).isSameAs(DiscoveryResult.EMPTY);
        assertThat(nonExistentServer.getHost()).isEqualTo("undefined");
        assertThat(nonExistentServer.getPort()).isEqualTo(-1);
    }
}
