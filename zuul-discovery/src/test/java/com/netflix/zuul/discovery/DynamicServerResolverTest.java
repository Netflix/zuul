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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.zuul.resolver.ResolverListener;
import java.security.cert.TrustAnchor;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DynamicServerResolverTest {


    @Test
    public void verifyListenerUpdates() {

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

        final CustomListener listener = new CustomListener();
        final DynamicServerResolver resolver = new DynamicServerResolver(new DefaultClientConfigImpl(), listener);

        final InstanceInfo first = Builder.newBuilder()
                .setAppName("zuul-discovery-1")
                .setHostName("zuul-discovery-1")
                .setIPAddr("100.10.10.1")
                .setPort(443)
                .build();
        final InstanceInfo second = Builder.newBuilder()
                .setAppName("zuul-discovery-2")
                .setHostName("zuul-discovery-2")
                .setIPAddr("100.10.10.2")
                .setPort(443)
                .build();
        final DiscoveryEnabledServer server1 = new DiscoveryEnabledServer(first, true);
        final DiscoveryEnabledServer server2 = new DiscoveryEnabledServer(second, true);

        resolver.onUpdate(ImmutableList.of(server1, server2), ImmutableList.of());

        Truth.assertThat(listener.updatedList()).containsExactly(new DiscoveryResult(server1), new DiscoveryResult(server2));
    }

    @Test
    public void properSentinelValueWhenServersUnavailable() {
        final DynamicServerResolver resolver = new DynamicServerResolver(new DefaultClientConfigImpl(), new ResolverListener<DiscoveryResult>() {
            @Override
            public void onChange(List<DiscoveryResult> removedSet) {
            }
        });

        final DiscoveryResult nonExistentServer = resolver.resolve(null);

        Truth.assertThat(nonExistentServer).isSameInstanceAs(DiscoveryResult.EMPTY);
        Truth.assertThat(nonExistentServer.getHost()).isEqualTo("undefined");
        Truth.assertThat(nonExistentServer.getPort()).isEqualTo(-1);
    }
}
