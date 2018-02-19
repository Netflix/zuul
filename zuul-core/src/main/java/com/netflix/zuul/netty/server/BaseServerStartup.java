/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.server;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.ChannelConfigValue;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.netty.ratelimiting.NullChannelHandlerProvider;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Map;

public abstract class BaseServerStartup
{
    protected static final Logger LOG = LoggerFactory.getLogger(BaseServerStartup.class);

    protected final ServerStatusManager serverStatusManager;
    protected final Registry registry;
    protected final DirectMemoryMonitor directMemoryMonitor;
    protected final EventLoopGroupMetrics eventLoopGroupMetrics;
    protected final EurekaClient discoveryClient;
    protected final ApplicationInfoManager applicationInfoManager;
    protected final AccessLogPublisher accessLogPublisher;
    protected final SessionContextDecorator sessionCtxDecorator;
    protected final RequestCompleteHandler reqCompleteHandler;
    protected final FilterLoader filterLoader;
    protected final FilterUsageNotifier usageNotifier;

    private Map<Integer, ChannelInitializer> portsToChannelInitializers;
    private ClientConnectionsShutdown clientConnectionsShutdown;
    private Server server;


    @Inject
    public BaseServerStartup(ServerStatusManager serverStatusManager, FilterLoader filterLoader,
                             SessionContextDecorator sessionCtxDecorator, FilterUsageNotifier usageNotifier,
                             RequestCompleteHandler reqCompleteHandler, Registry registry,
                             DirectMemoryMonitor directMemoryMonitor, EventLoopGroupMetrics eventLoopGroupMetrics,
                             EurekaClient discoveryClient, ApplicationInfoManager applicationInfoManager,
                             AccessLogPublisher accessLogPublisher)
    {
        this.serverStatusManager = serverStatusManager;
        this.registry = registry;
        this.directMemoryMonitor = directMemoryMonitor;
        this.eventLoopGroupMetrics = eventLoopGroupMetrics;
        this.discoveryClient = discoveryClient;
        this.applicationInfoManager = applicationInfoManager;
        this.accessLogPublisher = accessLogPublisher;
        this.sessionCtxDecorator = sessionCtxDecorator;
        this.reqCompleteHandler = reqCompleteHandler;
        this.filterLoader = filterLoader;
        this.usageNotifier = usageNotifier;
    }

    public Server server()
    {
        return server;
    }

    @PostConstruct
    public void init() throws Exception
    {
        ChannelConfig channelDeps = new ChannelConfig();
        addChannelDependencies(channelDeps);

        ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        clientConnectionsShutdown = new ClientConnectionsShutdown(clientChannels,
                GlobalEventExecutor.INSTANCE, discoveryClient);

        portsToChannelInitializers = choosePortsAndChannels(clientChannels, channelDeps);

        server = new Server(portsToChannelInitializers, serverStatusManager, clientConnectionsShutdown, eventLoopGroupMetrics);
    }

    protected abstract Map<Integer, ChannelInitializer> choosePortsAndChannels(
            ChannelGroup clientChannels,
            ChannelConfig channelDependencies);

    protected void addChannelDependencies(ChannelConfig channelDeps) throws Exception
    {
        channelDeps.set(ZuulDependencyKeys.registry, registry);

        channelDeps.set(ZuulDependencyKeys.applicationInfoManager, applicationInfoManager);
        channelDeps.set(ZuulDependencyKeys.serverStatusManager, serverStatusManager);

        channelDeps.set(ZuulDependencyKeys.accessLogPublisher, accessLogPublisher);

        channelDeps.set(ZuulDependencyKeys.sessionCtxDecorator, sessionCtxDecorator);
        channelDeps.set(ZuulDependencyKeys.requestCompleteHandler, reqCompleteHandler);
        final BasicCounter httpRequestReadTimeoutCounter =  new BasicCounter(MonitorConfig.builder("server.http.request.read.timeout").build());
        DefaultMonitorRegistry.getInstance().register(httpRequestReadTimeoutCounter);
        channelDeps.set(ZuulDependencyKeys.httpRequestReadTimeoutCounter, httpRequestReadTimeoutCounter);
        channelDeps.set(ZuulDependencyKeys.filterLoader, filterLoader);
        channelDeps.set(ZuulDependencyKeys.filterUsageNotifier, usageNotifier);

        channelDeps.set(ZuulDependencyKeys.eventLoopGroupMetrics, eventLoopGroupMetrics);

        channelDeps.set(ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDeps.set(ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());

        directMemoryMonitor.init();
    }


    public static ChannelConfig defaultChannelConfig()
    {
        ChannelConfig config = new ChannelConfig();

        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxConnections,
                new DynamicIntProperty("server.connection.max", 20000).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxRequestsPerConnection,
                new DynamicIntProperty("server.connection.max.requests", 20000).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout,
                new DynamicIntProperty("server.connection.max.requests.brownout", CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout.defaultValue()).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.connectionExpiry,
                new DynamicIntProperty("server.connection.expiry", CommonChannelConfigKeys.connectionExpiry.defaultValue()).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.idleTimeout,
                new DynamicIntProperty("server.connection.idle.timeout", 65 * 1000).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.httpRequestReadTimeout,
                new DynamicIntProperty("server.http.request.read.timeout", 5000).get()));

        // For security, default to NEVER allowing XFF/Proxy headers from client.
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER));

        config.set(CommonChannelConfigKeys.withProxyProtocol, true);
        config.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);

        config.add(new ChannelConfigValue(CommonChannelConfigKeys.connCloseDelay,
                new DynamicIntProperty("zuul.server.connection.close.delay", 10).get()));

        return config;
    }

    public static void addHttp2DefaultConfig(ChannelConfig config) {
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxConcurrentStreams,
                new DynamicIntProperty("server.http2.max.concurrent.streams", CommonChannelConfigKeys.maxConcurrentStreams.defaultValue()).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.initialWindowSize,
                new DynamicIntProperty("server.http2.initialwindowsize", CommonChannelConfigKeys.initialWindowSize.defaultValue()).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxHttp2HeaderTableSize,
                new DynamicIntProperty("server.http2.maxheadertablesize", 65536).get()));
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxHttp2HeaderListSize,
                new DynamicIntProperty("server.http2.maxheaderlistsize", 32768).get()));

        // Override this to a lower value, as we'll be using ELB TCP listeners for h2, and therefore the connection
        // is direct from each device rather than shared in an ELB pool.
        config.add(new ChannelConfigValue(CommonChannelConfigKeys.maxRequestsPerConnection,
                new DynamicIntProperty("server.connection.max.requests", 4000).get()));
    }

    protected void logPortConfigured(int port, ServerSslConfig serverSslConfig)
    {
        String msg = "Configured port: " + port;
        if (serverSslConfig != null) {
            msg = msg + " with SSL config: " + serverSslConfig.toString();
        }
        LOG.warn(msg);
    }
}
