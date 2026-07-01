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
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.DefaultEventLoopConfig;
import com.netflix.zuul.netty.server.DirectMemoryMonitor;
import com.netflix.zuul.netty.server.NamedSocketAddress;
import com.netflix.zuul.netty.server.SocketAddressProperty;
import com.netflix.zuul.netty.server.ZuulServerChannelInitializer;
import com.netflix.zuul.netty.server.http2.Http2SslChannelInitializer;
import com.netflix.zuul.netty.ssl.BaseSslContextFactory;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Brings up two listeners for integration testing: a cleartext HTTP/1.1 listener ("http") and a TLS HTTP/2 listener
 * ("http2") on a separate port. The HTTP/2 listener uses an in-memory self-signed certificate so tests stay fast and
 * self-contained. Both listeners disable the PROXY protocol so plain clients (OkHttp, etc.) can connect directly.
 */
@Singleton
public class ServerStartup extends BaseServerStartup {

    private static final String[] TLS_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};
    private static final String LISTEN_ADDRESS_NAME = "main";

    private ChannelGroup clientChannels;

    @Inject
    public ServerStartup(
            ServerStatusManager serverStatusManager,
            FilterLoader filterLoader,
            SessionContextDecorator sessionCtxDecorator,
            FilterUsageNotifier usageNotifier,
            RequestCompleteHandler reqCompleteHandler,
            Registry registry,
            DirectMemoryMonitor directMemoryMonitor,
            EventLoopGroupMetrics eventLoopGroupMetrics,
            EurekaClient discoveryClient,
            ApplicationInfoManager applicationInfoManager,
            AccessLogPublisher accessLogPublisher) {
        super(
                serverStatusManager,
                filterLoader,
                sessionCtxDecorator,
                usageNotifier,
                reqCompleteHandler,
                registry,
                directMemoryMonitor,
                eventLoopGroupMetrics,
                new DefaultEventLoopConfig(),
                discoveryClient,
                applicationInfoManager,
                accessLogPublisher);
    }

    @Override
    protected Map<NamedSocketAddress, ChannelInitializer<?>> chooseAddrsAndChannels(ChannelGroup clientChannels) {
        this.clientChannels = clientChannels;

        Map<NamedSocketAddress, ChannelInitializer<?>> addrsToChannels = new HashMap<>();

        SocketAddress httpAddr = addressFor("zuul.server.port.main", "zuul.server.addr.main", 7001);
        addrsToChannels.put(new NamedSocketAddress("http", httpAddr), http1Initializer(httpAddr, clientChannels));
        logAddrConfigured(httpAddr);

        SocketAddress http2Addr = addressFor("zuul.server.port.http2", "zuul.server.addr.http2", 7002);
        ServerSslConfig sslConfig = selfSignedSslConfig();
        addrsToChannels.put(
                new NamedSocketAddress("http2", http2Addr), http2Initializer(http2Addr, sslConfig, clientChannels));
        logAddrConfigured(http2Addr, sslConfig);

        return Collections.unmodifiableMap(addrsToChannels);
    }

    private ChannelInitializer<?> http1Initializer(SocketAddress addr, ChannelGroup clientChannels) {
        ChannelConfig channelConfig = defaultChannelConfig(LISTEN_ADDRESS_NAME);
        ChannelConfig channelDependencies = defaultChannelDependencies(LISTEN_ADDRESS_NAME);

        disableProxyProtocol(channelConfig);

        return new ZuulServerChannelInitializer(metricId(addr), channelConfig, channelDependencies, clientChannels) {
            @Override
            protected void addHttp1Handlers(ChannelPipeline pipeline) {
                super.addHttp1Handlers(pipeline);
                pipeline.addLast(new HttpContentCompressor((CompressionOptions[]) null));
            }
        };
    }

    private ChannelInitializer<?> http2Initializer(
            SocketAddress addr, ServerSslConfig sslConfig, ChannelGroup clientChannels) {
        ChannelConfig channelConfig = defaultChannelConfig(LISTEN_ADDRESS_NAME);
        ChannelConfig channelDependencies = defaultChannelDependencies(LISTEN_ADDRESS_NAME);

        disableProxyProtocol(channelConfig);
        channelConfig.set(CommonChannelConfigKeys.serverSslConfig, sslConfig);
        channelConfig.set(CommonChannelConfigKeys.sslContextFactory, new BaseSslContextFactory(registry, sslConfig));

        addHttp2DefaultConfig(channelConfig, LISTEN_ADDRESS_NAME);

        return new Http2SslChannelInitializer(metricId(addr), channelConfig, channelDependencies, clientChannels);
    }

    private static void disableProxyProtocol(ChannelConfig channelConfig) {
        channelConfig.set(
                CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.ALWAYS);
        channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, false);
        channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
        channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, false);
    }

    private static ServerSslConfig selfSignedSslConfig() {
        try {
            SelfSignedCertificate cert = new SelfSignedCertificate("localhost");
            return ServerSslConfig.builder()
                    .protocols(TLS_PROTOCOLS)
                    .ciphers(ServerSslConfig.getDefaultCiphers())
                    .certChainFile(cert.certificate())
                    .keyFile(cert.privateKey())
                    .build();
        } catch (CertificateException e) {
            throw new IllegalStateException("failed to generate a self-signed certificate for the http2 listener", e);
        }
    }

    private static SocketAddress addressFor(String portProperty, String addrProperty, int defaultPort) {
        int port = new DynamicIntProperty(portProperty, defaultPort).get();
        return new SocketAddressProperty(addrProperty, "=" + port).getValue();
    }

    private static String metricId(SocketAddress addr) {
        if (addr instanceof InetSocketAddress inet) {
            return String.valueOf(inet.getPort());
        }
        // Just pick something. This would likely be a UDS addr or a LocalChannel addr.
        return addr.toString();
    }

    public ChannelGroup getClientChannels() {
        return clientChannels;
    }
}
