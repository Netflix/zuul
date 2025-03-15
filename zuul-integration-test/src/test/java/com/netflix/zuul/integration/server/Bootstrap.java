/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.integration.server;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.BasicRequestCompleteHandler;
import com.netflix.zuul.DefaultFilterFactory;
import com.netflix.zuul.StaticFilterLoader;
import com.netflix.zuul.context.ZuulSessionContextDecorator;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.integration.server.filters.InboundRoutesFilter;
import com.netflix.zuul.integration.server.filters.NeedsBodyBufferedInboundFilter;
import com.netflix.zuul.integration.server.filters.NeedsBodyBufferedOutboundFilter;
import com.netflix.zuul.integration.server.filters.RequestHeaderFilter;
import com.netflix.zuul.integration.server.filters.ResponseHeaderFilter;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.netty.server.DirectMemoryMonitor;
import com.netflix.zuul.netty.server.Server;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private static final Set<? extends Class<? extends ZuulFilter<?, ?>>> FILTER_TYPES;

    static {
        Set<Class<? extends ZuulFilter<?, ?>>> classes = new LinkedHashSet<>();
        classes.add(InboundRoutesFilter.class);
        classes.add(NeedsBodyBufferedInboundFilter.class);
        classes.add(RequestHeaderFilter.class);
        classes.add(ResponseHeaderFilter.class);
        classes.add(NeedsBodyBufferedOutboundFilter.class);

        FILTER_TYPES = Collections.unmodifiableSet(classes);
    }

    private Server server;
    private int exitCode = 0;

    public void start() {
        long startNanos = System.nanoTime();
        logger.info("Zuul: starting up.");

        try {
            Registry registry = new DefaultRegistry();
            AccessLogPublisher accessLogPublisher = new AccessLogPublisher(
                    "ACCESS", (channel, httpRequest) -> ClientRequestReceiver.getRequestFromChannel(channel)
                            .getContext()
                            .getUUID());
            ServerStartup serverStartup = new ServerStartup(
                    new NoOpServerStatusManager(),
                    new StaticFilterLoader(new DefaultFilterFactory(), FILTER_TYPES),
                    new ZuulSessionContextDecorator(new BasicNettyOriginManager(registry)),
                    (f, s) -> {},
                    new BasicRequestCompleteHandler(),
                    registry,
                    new DirectMemoryMonitor(registry),
                    new EventLoopGroupMetrics(registry),
                    new NoOpEurekaClient(),
                    new ApplicationInfoManager(null, null, null),
                    accessLogPublisher,
                    new PushConnectionRegistry());
            serverStartup.init();
            server = serverStartup.server();

            server.start();
            long startupDuration = System.nanoTime() - startNanos;
            logger.info("Zuul: finished startup. Duration = {}ms", TimeUnit.NANOSECONDS.toMillis(startupDuration));
            // server.awaitTermination();
        } catch (Throwable t) {
            exitCode = 1;
            throw new RuntimeException(t);
        }
    }

    public Server getServer() {
        return this.server;
    }

    public boolean isRunning() {
        return (server != null) && (server.getListeningAddresses().size() > 0);
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private static class NoOpEurekaClient implements EurekaClient {
        @Override
        public Applications getApplicationsForARegion(@Nullable String region) {
            return null;
        }

        @Override
        public Applications getApplications(String serviceUrl) {
            return null;
        }

        @Override
        public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure) {
            return List.of();
        }

        @Override
        public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure, @Nullable String region) {
            return List.of();
        }

        @Override
        public List<InstanceInfo> getInstancesByVipAddressAndAppName(
                String vipAddress, String appName, boolean secure) {
            return List.of();
        }

        @Override
        public Set<String> getAllKnownRegions() {
            return Set.of();
        }

        @Override
        public InstanceInfo.InstanceStatus getInstanceRemoteStatus() {
            return null;
        }

        @Override
        public List<String> getDiscoveryServiceUrls(String zone) {
            return List.of();
        }

        @Override
        public List<String> getServiceUrlsFromConfig(String instanceZone, boolean preferSameZone) {
            return List.of();
        }

        @Override
        public List<String> getServiceUrlsFromDNS(String instanceZone, boolean preferSameZone) {
            return List.of();
        }

        @Override
        public void registerHealthCheckCallback(HealthCheckCallback callback) {}

        @Override
        public void registerHealthCheck(HealthCheckHandler healthCheckHandler) {}

        @Override
        public void registerEventListener(EurekaEventListener eventListener) {}

        @Override
        public boolean unregisterEventListener(EurekaEventListener eventListener) {
            return false;
        }

        @Override
        public HealthCheckHandler getHealthCheckHandler() {
            return null;
        }

        @Override
        public void shutdown() {}

        @Override
        public EurekaClientConfig getEurekaClientConfig() {
            return null;
        }

        @Override
        public ApplicationInfoManager getApplicationInfoManager() {
            return null;
        }

        @Override
        public Application getApplication(String appName) {
            return null;
        }

        @Override
        public Applications getApplications() {
            return null;
        }

        @Override
        public List<InstanceInfo> getInstancesById(String id) {
            return List.of();
        }

        @Override
        public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
            return null;
        }
    }

    private static class NoOpServerStatusManager extends ServerStatusManager {

        public NoOpServerStatusManager() {
            super(null);
        }

        @Override
        public void localStatus(InstanceInfo.InstanceStatus status) {}
    }
}
