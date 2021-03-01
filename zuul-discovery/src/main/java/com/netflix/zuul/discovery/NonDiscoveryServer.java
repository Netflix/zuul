package com.netflix.zuul.discovery;

import com.netflix.loadbalancer.Server;

/**
 * @author Argha C
 * @since 3/1/21
 * <p>
 * This exists merely to wrap a resolver lookup result, that is not discovery enabled.
 */
public class NonDiscoveryServer implements ResolverResult {

    private final Server server;

    public NonDiscoveryServer(String host, int port) {
        this.server = new Server(host, port);
    }

    @Override
    public String getHost() {
        return server.getHost();
    }

    @Override
    public int getPort() {
        return server.getPort();
    }

    @Override
    public boolean isDiscoveryEnabled() {
        return false;
    }
}
