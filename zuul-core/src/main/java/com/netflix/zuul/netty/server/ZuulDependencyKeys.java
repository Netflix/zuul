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
import com.netflix.discovery.DiscoveryClient;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfigKey;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import io.netty.channel.ChannelHandler;

import javax.inject.Provider;

/**
 * User: michaels@netflix.com
 * Date: 2/9/17
 * Time: 9:35 AM
 */
public class ZuulDependencyKeys {

    public static final ChannelConfigKey<AccessLogPublisher> accessLogPublisher = new ChannelConfigKey<>("accessLogPublisher");
    public static final ChannelConfigKey<EventLoopGroupMetrics> eventLoopGroupMetrics = new ChannelConfigKey<>("eventLoopGroupMetrics");
    public static final ChannelConfigKey<Registry> registry = new ChannelConfigKey<>("registry");
    public static final ChannelConfigKey<SessionContextDecorator> sessionCtxDecorator = new ChannelConfigKey<>("sessionCtxDecorator");
    public static final ChannelConfigKey<RequestCompleteHandler> requestCompleteHandler = new ChannelConfigKey<>("requestCompleteHandler");
    public static final ChannelConfigKey<BasicCounter> httpRequestReadTimeoutCounter = new ChannelConfigKey<>("httpRequestReadTimeoutCounter");
    public static final ChannelConfigKey<FilterLoader> filterLoader = new ChannelConfigKey<>("filterLoader");
    public static final ChannelConfigKey<FilterUsageNotifier> filterUsageNotifier = new ChannelConfigKey<>("filterUsageNotifier");
    public static final ChannelConfigKey<DiscoveryClient> discoveryClient = new ChannelConfigKey<>("discoveryClient");
    public static final ChannelConfigKey<ApplicationInfoManager> applicationInfoManager = new ChannelConfigKey<>("applicationInfoManager");
    public static final ChannelConfigKey<ServerStatusManager> serverStatusManager = new ChannelConfigKey<>("serverStatusManager");
    public static final ChannelConfigKey<Boolean> SSL_CLIENT_CERT_CHECK_REQUIRED = new ChannelConfigKey<>("requiresSslClientCertCheck", false);

    public static final ChannelConfigKey<Provider<ChannelHandler>> rateLimitingChannelHandlerProvider = new ChannelConfigKey<>("rateLimitingChannelHandlerProvider");
    public static final ChannelConfigKey<Provider<ChannelHandler>> sslClientCertCheckChannelHandlerProvider = new ChannelConfigKey<>("sslClientCertCheckChannelHandlerProvider");
    public static final ChannelConfigKey<PushConnectionRegistry> pushConnectionRegistry = new ChannelConfigKey<>("pushConnectionRegistry");
}
