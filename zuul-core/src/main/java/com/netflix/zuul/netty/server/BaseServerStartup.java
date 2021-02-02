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

import com.google.errorprone.annotations.ForOverride;
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
import com.netflix.spectator.api.Counter;
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
import io.netty.util.AsyncMapping;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseServerStartup
{
    protected static final Logger LOG = LoggerFactory.getLogger(BaseServerStartup.class);

    protected final ServerStatusManager serverStatusManager;
    protected final Registry registry;
    @SuppressWarnings("unused") // force initialization
    protected final DirectMemoryMonitor directMemoryMonitor;
    protected final EventLoopGroupMetrics eventLoopGroupMetrics;
    protected final EurekaClient discoveryClient;
    protected final ApplicationInfoManager applicationInfoManager;
    protected final AccessLogPublisher accessLogPublisher;
    protected final SessionContextDecorator sessionCtxDecorator;
    protected final RequestCompleteHandler reqCompleteHandler;
    protected final FilterLoader filterLoader;
    protected final FilterUsageNotifier usageNotifier;

    private Map<NamedSocketAddress, ? extends ChannelInitializer<?>> addrsToChannelInitializers;
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

    @Inject
    public void init() throws Exception
    {
        ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        clientConnectionsShutdown = new ClientConnectionsShutdown(clientChannels,
                GlobalEventExecutor.INSTANCE, discoveryClient);

        addrsToChannelInitializers = chooseAddrsAndChannels(clientChannels);

        server = new Server(
                registry,
                serverStatusManager,
                addrsToChannelInitializers,
                clientConnectionsShutdown,
                eventLoopGroupMetrics,
                new DefaultEventLoopConfig());
    }

    // TODO(carl-mastrangelo): remove this after 2.1.7
    /**
     * Use {@link #chooseAddrsAndChannels(ChannelGroup)} instead.
     */
    @Deprecated
    protected Map<Integer, ChannelInitializer> choosePortsAndChannels(ChannelGroup clientChannels) {
        throw new UnsupportedOperationException("unimplemented");
    }

    @ForOverride
    protected Map<NamedSocketAddress, ChannelInitializer<?>> chooseAddrsAndChannels(ChannelGroup clientChannels) {
        @SuppressWarnings("unchecked") // Channel init map has the wrong generics and we can't fix without api breakage.
        Map<Integer, ChannelInitializer<?>> portMap =
                (Map<Integer, ChannelInitializer<?>>) (Map) choosePortsAndChannels(clientChannels);
        return Server.convertPortMap(portMap);
    }


    protected ChannelConfig defaultChannelDependencies(String listenAddressName) {
        ChannelConfig channelDependencies = new ChannelConfig();
        addChannelDependencies(channelDependencies, listenAddressName);
        return channelDependencies;
    }

    protected void addChannelDependencies(
            ChannelConfig channelDeps,
            @SuppressWarnings("unused") String listenAddressName) { // listenAddressName is used by subclasses
        channelDeps.set(ZuulDependencyKeys.registry, registry);

        channelDeps.set(ZuulDependencyKeys.applicationInfoManager, applicationInfoManager);
        channelDeps.set(ZuulDependencyKeys.serverStatusManager, serverStatusManager);

        channelDeps.set(ZuulDependencyKeys.accessLogPublisher, accessLogPublisher);

        channelDeps.set(ZuulDependencyKeys.sessionCtxDecorator, sessionCtxDecorator);
        channelDeps.set(ZuulDependencyKeys.requestCompleteHandler, reqCompleteHandler);
        final Counter httpRequestReadTimeoutCounter = registry.counter("server.http.request.read.timeout");
        channelDeps.set(ZuulDependencyKeys.httpRequestReadTimeoutCounter, httpRequestReadTimeoutCounter);
        channelDeps.set(ZuulDependencyKeys.filterLoader, filterLoader);
        channelDeps.set(ZuulDependencyKeys.filterUsageNotifier, usageNotifier);

        channelDeps.set(ZuulDependencyKeys.eventLoopGroupMetrics, eventLoopGroupMetrics);

        channelDeps.set(ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDeps.set(ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());
    }

    /**
     * First looks for a property specific to the named listen address of the form -
     * "server.${addrName}.${propertySuffix}". If none found, then looks for a server-wide property of the form -
     * "server.${propertySuffix}".  If that is also not found, then returns the specified default value.
     */
    public static int chooseIntChannelProperty(String listenAddressName, String propertySuffix, int defaultValue) {
        String globalPropertyName = "server." + propertySuffix;
        String listenAddressPropertyName = "server." + listenAddressName + "." + propertySuffix;
        Integer value = new DynamicIntProperty(listenAddressPropertyName, -999).get();
        if (value == -999) {
            value = new DynamicIntProperty(globalPropertyName, -999).get();
            if (value == -999) {
                value = defaultValue;
            }
        }
        return value;
    }

    public static boolean chooseBooleanChannelProperty(
            String listenAddressName, String propertySuffix, boolean defaultValue) {
        String globalPropertyName = "server." + propertySuffix;
        String listenAddressPropertyName = "server." + listenAddressName + "." + propertySuffix;

        Boolean value = new ChainedDynamicProperty.DynamicBooleanPropertyThatSupportsNull(
                listenAddressPropertyName, null).get();
        if (value == null) {
            value = new DynamicBooleanProperty(globalPropertyName, defaultValue).getDynamicProperty().getBoolean();
            if (value == null) {
                value = defaultValue;
            }
        }
        return value;
    }

    public static ChannelConfig defaultChannelConfig(String listenAddressName) {
        ChannelConfig config = new ChannelConfig();

        config.add(new ChannelConfigValue<>(
                CommonChannelConfigKeys.maxConnections,
                chooseIntChannelProperty(
                        listenAddressName, "connection.max", CommonChannelConfigKeys.maxConnections.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxRequestsPerConnection,
                chooseIntChannelProperty(listenAddressName, "connection.max.requests", 20000)));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout,
                chooseIntChannelProperty(
                        listenAddressName,
                        "connection.max.requests.brownout",
                        CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.connectionExpiry,
                chooseIntChannelProperty(
                        listenAddressName,
                        "connection.expiry",
                        CommonChannelConfigKeys.connectionExpiry.defaultValue())));
        config.add(new ChannelConfigValue<>(
                CommonChannelConfigKeys.httpRequestReadTimeout,
                chooseIntChannelProperty(
                        listenAddressName,
                        "http.request.read.timeout",
                        CommonChannelConfigKeys.httpRequestReadTimeout.defaultValue())));

        int connectionIdleTimeout = chooseIntChannelProperty(
                listenAddressName, "connection.idle.timeout",
                CommonChannelConfigKeys.idleTimeout.defaultValue());
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.idleTimeout, connectionIdleTimeout));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.serverTimeout, new ServerTimeout(connectionIdleTimeout)));

        // For security, default to NEVER allowing XFF/Proxy headers from client.
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER));

        config.set(CommonChannelConfigKeys.withProxyProtocol, true);
        config.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);

        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.connCloseDelay,
                chooseIntChannelProperty(
                        listenAddressName,
                        "connection.close.delay",
                        CommonChannelConfigKeys.connCloseDelay.defaultValue())));

        return config;
    }

    public static void addHttp2DefaultConfig(ChannelConfig config, String listenAddressName) {
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxConcurrentStreams,
                chooseIntChannelProperty(
                        listenAddressName,
                        "http2.max.concurrent.streams",
                        CommonChannelConfigKeys.maxConcurrentStreams.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.initialWindowSize,
                chooseIntChannelProperty(
                        listenAddressName,
                        "http2.initialwindowsize",
                        CommonChannelConfigKeys.initialWindowSize.defaultValue())));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxHttp2HeaderTableSize,
                chooseIntChannelProperty(listenAddressName, "http2.maxheadertablesize", 65536)));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxHttp2HeaderListSize,
                chooseIntChannelProperty(listenAddressName, "http2.maxheaderlistsize", 32768)));

        // Override this to a lower value, as we'll be using ELB TCP listeners for h2, and therefore the connection
        // is direct from each device rather than shared in an ELB pool.
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.maxRequestsPerConnection,
                chooseIntChannelProperty(listenAddressName, "connection.max.requests", 4000)));

        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.http2AllowGracefulDelayed,
                chooseBooleanChannelProperty(listenAddressName, "connection.close.graceful.delayed.allow", true)));
        config.add(new ChannelConfigValue<>(CommonChannelConfigKeys.http2SwallowUnknownExceptionsOnConnClose,
                chooseBooleanChannelProperty(listenAddressName, "connection.close.swallow.unknown.exceptions", false)));
    }

    // TODO(carl-mastrangelo): remove this after 2.1.7
    /**
     * Use {@link #logAddrConfigured(SocketAddress)} instead.
     */
    @Deprecated
    protected void logPortConfigured(int port) {
        logAddrConfigured(new InetSocketAddress(port));
    }

    // TODO(carl-mastrangelo): remove this after 2.1.7
    /**
     * Use {@link #logAddrConfigured(SocketAddress, ServerSslConfig)} instead.
     */
    @Deprecated
    protected void logPortConfigured(int port, ServerSslConfig serverSslConfig) {
        logAddrConfigured(new InetSocketAddress(port), serverSslConfig);
    }

    // TODO(carl-mastrangelo): remove this after 2.1.7
    /**
     * Use {@link #logAddrConfigured(SocketAddress, AsyncMapping)} instead.
     */
    @Deprecated
    protected void logPortConfigured(int port, AsyncMapping<String, SslContext> sniMapping) {
        logAddrConfigured(new InetSocketAddress(port), sniMapping);
    }

    protected final void logAddrConfigured(SocketAddress socketAddress) {
        LOG.info("Configured address: {}", socketAddress);
    }

    protected final void logAddrConfigured(SocketAddress socketAddress, @Nullable ServerSslConfig serverSslConfig) {
        String msg = "Configured address: " + socketAddress;
        if (serverSslConfig != null) {
            msg = msg + " with SSL config: " + serverSslConfig;
        }
        LOG.info(msg);
    }

    protected final void logAddrConfigured(
            SocketAddress socketAddress, @Nullable AsyncMapping<String, SslContext> sniMapping) {
        String msg = "Configured address: " + socketAddress;
        if (sniMapping != null) {
            msg = msg + " with SNI config: " + sniMapping;
        }
        LOG.info(msg);
    }

    protected final void logSecureAddrConfigured(SocketAddress socketAddress, @Nullable Object securityConfig) {
        LOG.info("Configured address: {} with security config {}", socketAddress, securityConfig);
    }
}
