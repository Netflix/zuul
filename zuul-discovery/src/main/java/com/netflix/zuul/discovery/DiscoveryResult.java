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
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * @author Argha C
 * @since 2/25/21
 * <p>
 * Wraps a single instance of discovery enabled server, and stats related to it.
 */
public final class DiscoveryResult implements ResolverResult {

    private final DiscoveryEnabledServer server;
    private final ServerStats serverStats;
    /**
     * This exists to allow for a semblance of type safety, and encourages avoiding null checks on the underlying Server,
     * thus representing a sentinel value for an empty resolution result.
     */
    public static final DiscoveryResult EMPTY =
            DiscoveryResult.from(InstanceInfo.Builder.newBuilder()
                                .setAppName("undefined")
                                .setHostName("undefined")
                                .setPort(-1).build(), false);

    public DiscoveryResult(DiscoveryEnabledServer server, LoadBalancerStats lbStats) {
        this.server = server;
        Objects.requireNonNull(lbStats, "Loadbalancer stats must be a valid instance");
        this.serverStats = lbStats.getSingleServerStat(server);
    }

    /**
     *
     * This solely exists to create a result object from incomplete InstanceInfo.
     * Usage of this for production code is strongly discouraged, since the underlying instances are prone to memory leaks
     */
    public DiscoveryResult(DiscoveryEnabledServer server) {
        this.server = server;
        this.serverStats = new ServerStats();
    }

    /**
     *
     * This convenience method exists for usage in tests. For production usage, please use the constructor linked:
     * @see DiscoveryResult#DiscoveryResult(DiscoveryEnabledServer, LoadBalancerStats)
     */
    @VisibleForTesting
    public static DiscoveryResult from(InstanceInfo instanceInfo, boolean useSecurePort) {
        final DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, useSecurePort);
        return new DiscoveryResult(server);
    }

    public Optional<String> getIPAddr() {
        if (this == DiscoveryResult.EMPTY) {
            return Optional.empty();
        }
        if (server.getInstanceInfo() != null) {
            String ip = server.getInstanceInfo().getIPAddr();
            if (ip != null && !ip.isEmpty()) {
                return Optional.of(ip);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public String getHost() {
        return server == null ? "undefined" : server.getHost();
    }

    @Override
    public boolean isDiscoveryEnabled() {
        return server instanceof DiscoveryEnabledServer;
    }

    @Override
    public int getPort() {
        return server == null ? -1 : server.getPort();
    }

    public int getSecurePort() {
        return server.getInstanceInfo().getSecurePort();
    }

    public boolean isSecurePortEnabled() {
        return server.getPort() == server.getInstanceInfo().getSecurePort();
    }

    public String getTarget() {
        final InstanceInfo instanceInfo = server.getInstanceInfo();
        if (server.getPort() == instanceInfo.getSecurePort()) {
            return instanceInfo.getSecureVipAddress();
        } else {
            return instanceInfo.getVIPAddress();
        }
    }


    public SimpleMetaInfo getMetaInfo() {
        return new SimpleMetaInfo(server.getMetaInfo());
    }

    @Nullable
    public String getAvailabilityZone() {
        final InstanceInfo instanceInfo = server.getInstanceInfo();
        if (instanceInfo.getDataCenterInfo() instanceof AmazonInfo) {
            return ((AmazonInfo) instanceInfo.getDataCenterInfo()).getMetadata().get("availability-zone");
        }
        return null;
    }

    public String getZone() {
        return server.getZone();
    }

    public String getServerId() {
        return server.getInstanceInfo().getId();
    }

    public DiscoveryEnabledServer getServer() {
        return server;
    }

    @VisibleForTesting
    ServerStats getServerStats(){
        return this.serverStats;
    }

    public String getASGName() {
        return server.getInstanceInfo().getASGName();
    }

    public String getAppName() {
        return server.getInstanceInfo().getAppName().toLowerCase(Locale.ROOT);
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

    public long getTotalRequestsCount() {
        return serverStats.getTotalRequestsCount();
    }

    public int getActiveRequestsCount() {
        return serverStats.getActiveRequestsCount();
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
        return Objects.hashCode(server);
    }


    /**
     * Two instances are deemed identical if they wrap the same underlying discovery server instance.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof DiscoveryResult)) {
            return false;
        }
        final DiscoveryResult other = (DiscoveryResult) obj;
        return server.equals(other.server);
    }

}
