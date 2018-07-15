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

package com.netflix.zuul.sample;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.netty.server.*;
import com.netflix.zuul.netty.server.http2.Http2SslChannelInitializer;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.ssl.BaseSslContextFactory;
import com.netflix.zuul.sample.push.SampleSSEPushChannelInitializer;
import com.netflix.zuul.sample.push.SampleWebSocketPushChannelInitializer;
import com.netflix.zuul.sample.push.SamplePushMessageSenderInitializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.ClientAuth;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.netty.common.status.ServerStatusManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Sample Server Startup - class that configures the Netty server startup settings
 *
 * Author: Arthur Gonigberg
 * Date: November 20, 2017
 */
@Singleton
public class SampleServerStartup extends BaseServerStartup {

    enum ServerType {
        HTTP,
        HTTP2,
        HTTP_MUTUAL_TLS,
        WEBSOCKET,
        SSE
    }

    private static final String[] WWW_PROTOCOLS = new String[]{"TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3"};
    private static final ServerType SERVER_TYPE = ServerType.HTTP;
    private final PushConnectionRegistry pushConnectionRegistry;
    private final SamplePushMessageSenderInitializer pushSenderInitializer;

    @Inject
    public SampleServerStartup(ServerStatusManager serverStatusManager, FilterLoader filterLoader,
                               SessionContextDecorator sessionCtxDecorator, FilterUsageNotifier usageNotifier,
                               RequestCompleteHandler reqCompleteHandler, Registry registry,
                               DirectMemoryMonitor directMemoryMonitor, EventLoopGroupMetrics eventLoopGroupMetrics,
                               EurekaClient discoveryClient, ApplicationInfoManager applicationInfoManager,
                               AccessLogPublisher accessLogPublisher, PushConnectionRegistry pushConnectionRegistry,
                               SamplePushMessageSenderInitializer pushSenderInitializer) {
        super(serverStatusManager, filterLoader, sessionCtxDecorator, usageNotifier, reqCompleteHandler, registry,
                directMemoryMonitor, eventLoopGroupMetrics, discoveryClient, applicationInfoManager,
                accessLogPublisher);
        this.pushConnectionRegistry = pushConnectionRegistry;
        this.pushSenderInitializer = pushSenderInitializer;
    }

    @Override
    protected Map<Integer, ChannelInitializer> choosePortsAndChannels(
            ChannelGroup clientChannels,
            ChannelConfig channelDependencies) {
        Map<Integer, ChannelInitializer> portsToChannels = new HashMap<>();

        int port = new DynamicIntProperty("zuul.server.port.main", 7001).get();

        ChannelConfig channelConfig = BaseServerStartup.defaultChannelConfig();
        int pushPort = new DynamicIntProperty("zuul.server.port.http.push", 7008).get();
        ServerSslConfig sslConfig;
        /* These settings may need to be tweaked depending if you're running behind an ELB HTTP listener, TCP listener,
         * or directly on the internet.
         */
        switch (SERVER_TYPE) {
            /* The below settings can be used when running behind an ELB HTTP listener that terminates SSL for you
             * and passes XFF headers.
             */
            case HTTP:
                channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.ALWAYS);
                channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, false);
                channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
                channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, false);

                portsToChannels.put(port, new ZuulServerChannelInitializer(port, channelConfig, channelDependencies, clientChannels));
                logPortConfigured(port, null);
                break;

            /* The below settings can be used when running behind an ELB TCP listener with proxy protocol, terminating
             * SSL in Zuul.
             */
            case HTTP2:
                sslConfig = ServerSslConfig.withDefaultCiphers(
                        loadFromResources("server.cert"),
                        loadFromResources("server.key"),
                        WWW_PROTOCOLS);

                channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER);
                channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);
                channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
                channelConfig.set(CommonChannelConfigKeys.serverSslConfig, sslConfig);
                channelConfig.set(CommonChannelConfigKeys.sslContextFactory, new BaseSslContextFactory(registry, sslConfig));

                addHttp2DefaultConfig(channelConfig);

                portsToChannels.put(port, new Http2SslChannelInitializer(port, channelConfig, channelDependencies, clientChannels));
                logPortConfigured(port, sslConfig);
                break;

            /* The below settings can be used when running behind an ELB TCP listener with proxy protocol, terminating
             * SSL in Zuul.
             *
             * Can be tested using certs in resources directory:
             *  curl https://localhost:7001/test -vk --cert src/main/resources/ssl/client.cert:zuul123 --key src/main/resources/ssl/client.key
             */
            case HTTP_MUTUAL_TLS:
                sslConfig = new ServerSslConfig(
                        WWW_PROTOCOLS,
                        ServerSslConfig.getDefaultCiphers(),
                        loadFromResources("server.cert"),
                        loadFromResources("server.key"),
                        null,
                        ClientAuth.REQUIRE,
                        loadFromResources("truststore.jks"),
                        loadFromResources("truststore.key"),
                        false);

                channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER);
                channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);
                channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
                channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, true);
                channelConfig.set(CommonChannelConfigKeys.serverSslConfig, sslConfig);
                channelConfig.set(CommonChannelConfigKeys.sslContextFactory, new BaseSslContextFactory(registry, sslConfig));

                portsToChannels.put(port, new Http1MutualSslChannelInitializer(port, channelConfig, channelDependencies, clientChannels));
                logPortConfigured(port, sslConfig);
                break;

            /* Settings to be used when running behind an ELB TCP listener with proxy protocol as a Push notification
             * server using WebSockets */
            case WEBSOCKET:
                channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER);
                channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);
                channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
                channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, true);

                channelDependencies.set(ZuulDependencyKeys.pushConnectionRegistry, pushConnectionRegistry);

                portsToChannels.put(port, new SampleWebSocketPushChannelInitializer(port, channelConfig, channelDependencies, clientChannels));
                logPortConfigured(port, null);

                // port to accept push message from the backend, should be accessible on internal network only.
                portsToChannels.put(pushPort, pushSenderInitializer);
                logPortConfigured(pushPort, null);

                break;

            /* Settings to be used when running behind an ELB TCP listener with proxy protocol as a Push notification
             * server using Server Sent Events (SSE) */
            case SSE:
                channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.NEVER);
                channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, true);
                channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
                channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, true);

                channelDependencies.set(ZuulDependencyKeys.pushConnectionRegistry, pushConnectionRegistry);

                portsToChannels.put(port, new SampleSSEPushChannelInitializer(port, channelConfig, channelDependencies, clientChannels));
                logPortConfigured(port, null);

                // port to accept push message from the backend, should be accessible on internal network only.
                portsToChannels.put(pushPort, pushSenderInitializer);
                logPortConfigured(pushPort, null);

                break;
        }

        return portsToChannels;
    }

    private File loadFromResources(String s) {
        return new File(ClassLoader.getSystemResource("ssl/" + s).getFile());
    }
}
