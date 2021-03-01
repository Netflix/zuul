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

import static com.netflix.client.config.CommonClientConfigKey.NFLoadBalancerClassName;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.zuul.resolver.Resolver;
import com.netflix.zuul.resolver.ResolverListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * @author Argha C
 * @since 2/25/21
 *
 * Implements a resolver, wrapping a ribbon load-balancer.
 */
public class DynamicServerResolver implements Resolver<DiscoveryResult> {

    private final DynamicServerListLoadBalancer<?> loadBalancer;
    ResolverListener<DiscoveryResult> listener;

    public DynamicServerResolver(IClientConfig clientConfig, ResolverListener<DiscoveryResult> listener) {
        this.loadBalancer = createLoadBalancer(clientConfig);
        this.loadBalancer.addServerListChangeListener(this::onUpdate);
        this.listener = listener;
    }

    @Override
    public DiscoveryResult resolve(@Nullable Object key) {
        final Server server = loadBalancer.chooseServer(key);
        return new DiscoveryResult((DiscoveryEnabledServer) server);
    }

    public boolean hasServers() {
        return !loadBalancer.getReachableServers().isEmpty();
    }

    public void shutdown() {
        loadBalancer.shutdown();
    }

    private DynamicServerListLoadBalancer<?> createLoadBalancer(IClientConfig clientConfig) {
        //TODO(argha-c): Revisit this style of LB initialization post modularization. Ideally the LB should be pluggable.

        // Use a hard coded string for the LB default name to avoid a dependency on Ribbon classes.
        String loadBalancerClassName =
                clientConfig.get(NFLoadBalancerClassName, "com.netflix.loadbalancer.ZoneAwareLoadBalancer");

        DynamicServerListLoadBalancer<?> lb;
        try {
            Class<?> clazz = Class.forName(loadBalancerClassName);
            lb = clazz.asSubclass(DynamicServerListLoadBalancer.class).getConstructor().newInstance();
            lb.initWithNiwsConfig(clientConfig);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new IllegalStateException("Could not instantiate LoadBalancer " + loadBalancerClassName, e);
        }

        return lb;
    }

    @VisibleForTesting
    void onUpdate(List<Server> oldList, List<Server> newList) {
        Set<Server> oldSet = new HashSet<>(oldList);
        Set<Server> newSet = new HashSet<>(newList);
        final List<DiscoveryResult> discoveryResults = Sets.difference(oldSet, newSet).stream()
                .map(server -> new DiscoveryResult((DiscoveryEnabledServer) server))
                .collect(Collectors.toList());
        listener.onChange(discoveryResults);
    }
}
