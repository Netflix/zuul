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
import com.netflix.config.ChainedDynamicProperty;
import com.netflix.config.DynamicBooleanProperty;
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
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainNameMapping;
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
        ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        clientConnectionsShutdown = new ClientConnectionsShutdown(clientChannels,
                GlobalEventExecutor.INSTANCE, discoveryClient);

        portsToChannelInitializers = choosePortsAndChannels(clientChannels);

        directMemoryMonitor.init();

        server = new Server(portsToChannelInitializers, serverStatusManager, clientConnectionsShutdown, eventLoopGroupMetrics);
    }

    protected abstract Map<Integer, ChannelInitializer> choosePortsAndChannels(ChannelGroup clientChannels);

    protected ChannelConfig defaultChannelDependencies(String portName)
    {
        ChannelConfig channelDependencies = new ChannelConfig();
        addChannelDependencies(channelDependencies, portName);
        return channelDependencies;
    }

    protected void addChannelDependencies(ChannelConfig channelDeps, String portName)
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
    }

    /**
     * First looks for a property specific to the named port of the form - "server.${portName}.${propertySuffix}".
     * If none found, then looks for a server-wide property of the form - "server.${propertySuffix}".
     * If that is also not found, then returns the specified default value.
     *
     * @param portName
     * @param propertySuffix
     * @param defaultValue
     * @return
     */
    public static int chooseIntChannelProperty(String portName, String propertySuffix, int defaultValue)
    {
        String globalPropertyName = "server." + propertySuffix;
        String portPropertyName = "server." + portName + "." + propertySuffix;
        Integer value = new DynamicIntProperty(portPropertyName, -999).get();
        if (value == -999) {
            value = new DynamicIntProperty(globalPropertyName, -999).get();
            if (value == -999) {
                value = defaultValue;
            }
        }
        return value;
    }

    public static boolean chooseBooleanChannelProperty(String portName, String propertySuffix, boolean defaultValue)
    {
        String globalPropertyName = "server." + propertySuffix;
        String portPropertyName = "server." + portName + "." + propertySuffix;

        Boolean value = new ChainedDynamicProperty.DynamicBooleanPropertyThatSupportsNull(portPropertyName, null).get();
        if (value == null) {
            value = new DynamicBooleanProperty(globalPropertyName, defaultValue).getDynamicProperty().getBoolean();
            if (value == null) {
                value = defaultValue;
            }
        }
        return value;
    }

    public static ChannelConfig defaultChannelConfig(String portName)
    {
        ChannelConfig config = new ChannelConfig();

        config.add(new ChannelConfigValue<>(
                CommonChannelConfigKeys.maxConnections,
                chooseIntChannelProperty(
                        portName, "connection.max", CommonChannelConfigKeys.maxConnections.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxRequestsPerConnection,
                chooseIntChannelProperty(portName, "connection.max.requests", 20000)));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout,
                chooseIntChannelProperty(portName, "connection.max.requests.brownout", CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.connectionExpiry,
                chooseIntChannelProperty(portName, "connection.expiry", CommonChannelConfigKeys.connectionExpiry.defaultValue())));
        config.add(new ChannelConfigValue<>(
                CommonChannelConfigKeys.httpRequestReadTimeout,
                chooseIntChannelProperty(
                        portName,
                        "http.request.read.timeout",
                        CommonChannelConfigKeys.httpRequestReadTimeout.defaultValue())));

        int connectionIdleTimeout = chooseIntChannelProperty(
                portName, "connection.idle.timeout",
                CommonChannelConfigKeys.idleTimeout.defaultValue());
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.idleTimeout, connectionIdleTimeout));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.serverTimeout, new ServerTimeout(connectionIdleTimeout)));

        // For security, default to NEVER allowing XFF/Proxy headers from client.
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER));

        config.set(CommonChannelConfigKeys.withProxyProtocol, true);
        config.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);

        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.connCloseDelay,
                chooseIntChannelProperty(
                        portName, "connection.close.delay", CommonChannelConfigKeys.connCloseDelay.defaultValue())));

        return config;
    }

    public static void addHttp2DefaultConfig(ChannelConfig config, String portName)
    {
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxConcurrentStreams,
                chooseIntChannelProperty(portName, "http2.max.concurrent.streams", CommonChannelConfigKeys.maxConcurrentStreams.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.initialWindowSize,
                chooseIntChannelProperty(portName, "http2.initialwindowsize", CommonChannelConfigKeys.initialWindowSize.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxHttp2HeaderTableSize,
                chooseIntChannelProperty(portName, "http2.maxheadertablesize", 65536)));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxHttp2HeaderListSize,
                chooseIntChannelProperty(portName, "http2.maxheaderlistsize", 32768)));

        // Override this to a lower value, as we'll be using ELB TCP listeners for h2, and therefore the connection
        // is direct from each device rather than shared in an ELB pool.
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxRequestsPerConnection,
                chooseIntChannelProperty(portName, "connection.max.requests", 4000)));

        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.http2AllowGracefulDelayed,
                chooseBooleanChannelProperty(portName, "connection.close.graceful.delayed.allow", true)));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.http2SwallowUnknownExceptionsOnConnClose,
                chooseBooleanChannelProperty(portName, "connection.close.swallow.unknown.exceptions", false)));
    }

    protected void logPortConfigured(int port)
    {
        logPortConfigured(port, (ServerSslConfig) null);
    }

    protected void logPortConfigured(int port, ServerSslConfig serverSslConfig)
    {
        String msg = "Configured port: " + port;
        if (serverSslConfig != null) {
            msg = msg + " with SSL config: " + serverSslConfig.toString();
        }
        LOG.warn(msg);
    }

    protected void logPortConfigured(int port, DomainNameMapping<SslContext> sniMapping)
    {
        String msg = "Configured port: " + port;
        if (sniMapping != null) {
            msg = msg + " with SNI config: " + sniMapping.asMap().toString();
        }
        LOG.warn(msg);
    }
}
