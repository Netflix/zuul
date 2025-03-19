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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.zuul.resolver.Resolver;
import com.netflix.zuul.resolver.ResolverListener;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Argha C
 * @since 2/25/21
 * <p>
 * Implements a resolver, wrapping a ribbon load-balancer.
 */
public class DynamicServerResolver implements Resolver<DiscoveryResult> {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicServerResolver.class);

    private final DynamicServerListLoadBalancer<?> loadBalancer;
    private ResolverListener<DiscoveryResult> listener;

    @Deprecated
    public DynamicServerResolver(IClientConfig clientConfig, ResolverListener<DiscoveryResult> listener) {
        this.loadBalancer = createLoadBalancer(clientConfig);
        this.loadBalancer.addServerListChangeListener(this::onUpdate);
        this.listener = listener;
    }

    public DynamicServerResolver(IClientConfig clientConfig) {
        this(createLoadBalancer(clientConfig));
    }

    public DynamicServerResolver(DynamicServerListLoadBalancer<?> loadBalancer) {
        this.loadBalancer = Objects.requireNonNull(loadBalancer);
    }

    @Override
    public void setListener(ResolverListener<DiscoveryResult> listener) {
        if (this.listener != null) {
            LOG.warn("Ignoring call to setListener, because a listener was already set");
            return;
        }

        this.listener = Objects.requireNonNull(listener);
        this.loadBalancer.addServerListChangeListener(this::onUpdate);
    }

    @Override
    public DiscoveryResult resolve(@Nullable Object key) {
        Server server = loadBalancer.chooseServer(key);
        return server != null
                ? new DiscoveryResult((DiscoveryEnabledServer) server, loadBalancer.getLoadBalancerStats())
                : DiscoveryResult.EMPTY;
    }

    @Override
    public boolean hasServers() {
        return !loadBalancer.getReachableServers().isEmpty();
    }

    @Override
    public void shutdown() {
        loadBalancer.shutdown();
    }

    private static DynamicServerListLoadBalancer<?> createLoadBalancer(IClientConfig clientConfig) {
        // TODO(argha-c): Revisit this style of LB initialization post modularization. Ideally the LB should be
        // pluggable.

        // Use a hard coded string for the LB default name to avoid a dependency on Ribbon classes.
        String loadBalancerClassName = clientConfig.get(
                CommonClientConfigKey.NFLoadBalancerClassName, "com.netflix.loadbalancer.ZoneAwareLoadBalancer");

        DynamicServerListLoadBalancer<?> lb;
        try {
            Class<?> clazz = Class.forName(loadBalancerClassName);
            lb = clazz.asSubclass(DynamicServerListLoadBalancer.class)
                    .getConstructor()
                    .newInstance();
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
        List<DiscoveryResult> discoveryResults = Sets.difference(oldSet, newSet).stream()
                .map(server ->
                        new DiscoveryResult((DiscoveryEnabledServer) server, loadBalancer.getLoadBalancerStats()))
                .collect(Collectors.toList());
        listener.onChange(discoveryResults);
    }
}
