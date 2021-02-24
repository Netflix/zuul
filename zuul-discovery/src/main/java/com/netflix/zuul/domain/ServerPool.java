package com.netflix.zuul.domain;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.netflix.client.config.CommonClientConfigKey.NFLoadBalancerClassName;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.zuul.listeners.ResolverListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Argha C
 * @since 2/24/21
 */
public class ServerPool {

    private final DynamicServerListLoadBalancer<?> loadBalancer;
    ResolverListener<OriginServer> listener;
    private final String originName;
    private static final Logger log = LoggerFactory.getLogger(ServerPool.class);


    public ServerPool(IClientConfig clientConfig, String originName, ResolverListener<OriginServer> listener) {
        this.loadBalancer =  createLoadBalancer(clientConfig);
        this.listener = listener;
        this.loadBalancer.addServerListChangeListener(this::removeMissingServerConnectionPools);
        this.originName = originName;
    }

    private DynamicServerListLoadBalancer<?> createLoadBalancer(IClientConfig clientConfig) {
        // Create and configure a loadbalancer for this vip.  Use a hard coded string for the LB default name to avoid
        // a dependency on Ribbon classes.
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


    private void removeMissingServerConnectionPools(List<Server> oldList, List<Server> newList) {
        Set<Server> oldSet = new HashSet<>(oldList);
        Set<Server> newSet = new HashSet<>(newList);
        final List<OriginServer> originServers = Sets.difference(oldSet, newSet).stream()
                .map(server -> new OriginServer((DiscoveryEnabledServer) server))
                .collect(Collectors.toList());
        listener.onChange(originServers);
    }

    public boolean hasServers() {
        return !loadBalancer.getReachableServers().isEmpty();
    }

    public void shutdown() {
        loadBalancer.shutdown();
    }

    public OriginServer chooseServer( @Nullable Object key) {
        final Server server = loadBalancer.chooseServer(key);
        checkNotNull(server, "Origin server returned by load balancer cannot be null");
        return new OriginServer((DiscoveryEnabledServer) server);
    }

}
