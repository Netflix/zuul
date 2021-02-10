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

package com.netflix.zuul.domain;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import java.util.Optional;

/**
 * @author Argha C
 * @since 2/10/21
 */
public final class OriginServer {

    private final DiscoveryEnabledServer server;
    private final ServerStats serverStats;

    public OriginServer(DiscoveryEnabledServer server) {
        this.server = server;
        serverStats = new ServerStats();
        serverStats.initialize(server);
    }

    public DiscoveryEnabledServer getServer() {
        return server;
    }

    public Optional<String> ipAddr() {
        if (server.getInstanceInfo() != null) {
            String ip = server.getInstanceInfo().getIPAddr();
            if (ip != null && !ip.isEmpty()) {
                return Optional.of(ip);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    public String getHost() {
        return server.getHost();
    }

    public int getPort() {
        return server.getPort();
    }

    public String getZone() {
        return server.getZone();
    }

    public InstanceInfo getInstanceInfo() {
        return server.getInstanceInfo();
    }

    public void noteResponseTime(double msecs) {
        serverStats.noteResponseTime(msecs);
    }

    public boolean isCircuitBreakerTripped() {
        return serverStats.isCircuitBreakerTripped();
    }

    public void incrementActiveRequestsCount() {
        serverStats.incrementActiveRequestsCount();
    }

    public void incrementOpenConnectionsCount() {
        serverStats.incrementOpenConnectionsCount();
    }

    public void incrementSuccessiveConnectionFailureCount() {
        serverStats.incrementSuccessiveConnectionFailureCount();
    }

    public void incrementNumRequests() {
        serverStats.incrementNumRequests();
    }

    public int getOpenConnectionsCount() {
        return serverStats.getOpenConnectionsCount();
    }

    public void decrementOpenConnectionsCount() {
        serverStats.decrementOpenConnectionsCount();
    }

    public void decrementActiveRequestsCount() {
        serverStats.decrementActiveRequestsCount();
    }

    public void clearSuccessiveConnectionFailureCount() {
        serverStats.clearSuccessiveConnectionFailureCount();
    }

    public void addToFailureCount() {
        serverStats.addToFailureCount();
    }

    public void stopPublishingStats() {
        serverStats.close();
    }


    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

}
