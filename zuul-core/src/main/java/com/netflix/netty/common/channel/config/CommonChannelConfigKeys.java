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

package com.netflix.netty.common.channel.config;

import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.zuul.netty.server.ServerTimeout;
import com.netflix.zuul.netty.ssl.SslContextFactory;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsyncMapping;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 6:21 PM
 */
public class CommonChannelConfigKeys
{
    public static final ChannelConfigKey<Boolean> withProxyProtocol = new ChannelConfigKey<>("withProxyProtocol", false);
    public static final ChannelConfigKey<StripUntrustedProxyHeadersHandler.AllowWhen> allowProxyHeadersWhen =
            new ChannelConfigKey<>("allowProxyHeadersWhen", StripUntrustedProxyHeadersHandler.AllowWhen.ALWAYS);
    public static final ChannelConfigKey<Boolean> preferProxyProtocolForClientIp = new ChannelConfigKey<>("preferProxyProtocolForClientIp", true);

    /** The Idle timeout of a connection, in milliseconds */
    public static final ChannelConfigKey<Integer> idleTimeout = new ChannelConfigKey<>("idleTimeout", 65000);
    public static final ChannelConfigKey<ServerTimeout> serverTimeout = new ChannelConfigKey<>("serverTimeout");
    /** The HTTP request read timeout, in milliseconds */
    public static final ChannelConfigKey<Integer> httpRequestReadTimeout =
            new ChannelConfigKey<>("httpRequestReadTimeout", 5000);
    /** The maximum number of inbound connections to proxy. */
    public static final ChannelConfigKey<Integer> maxConnections = new ChannelConfigKey<>("maxConnections", 20000);
    public static final ChannelConfigKey<Integer> maxRequestsPerConnection = new ChannelConfigKey<>("maxRequestsPerConnection", 4000);
    public static final ChannelConfigKey<Integer> maxRequestsPerConnectionInBrownout = new ChannelConfigKey<>("maxRequestsPerConnectionInBrownout", 100);
    public static final ChannelConfigKey<Integer> connectionExpiry = new ChannelConfigKey<>("connectionExpiry", 20 * 60 * 1000);

    // SSL:
    public static final ChannelConfigKey<Boolean> isSSlFromIntermediary = new ChannelConfigKey<>("isSSlFromIntermediary", false);
    public static final ChannelConfigKey<ServerSslConfig> serverSslConfig = new ChannelConfigKey<>("serverSslConfig");
    public static final ChannelConfigKey<SslContextFactory> sslContextFactory = new ChannelConfigKey<>("sslContextFactory");
    public static final ChannelConfigKey<AsyncMapping<String, SslContext>> sniMapping = new ChannelConfigKey<>("sniMapping");

    // HTTP/2 specific:
    public static final ChannelConfigKey<Integer> maxConcurrentStreams = new ChannelConfigKey<>("maxConcurrentStreams", 100);
    public static final ChannelConfigKey<Integer> initialWindowSize = new ChannelConfigKey<>("initialWindowSize", 5242880);  // 5MB
    /* The amount of time to wait before closing a connection that has the `Connection: Close` header, in seconds */
    public static final ChannelConfigKey<Integer> connCloseDelay = new ChannelConfigKey<>("connCloseDelay", 10);
    public static final ChannelConfigKey<Integer> maxHttp2HeaderTableSize = new ChannelConfigKey<>("maxHttp2HeaderTableSize", 4096);
    public static final ChannelConfigKey<Integer> maxHttp2HeaderListSize = new ChannelConfigKey<>("maxHttp2HeaderListSize");
    public static final ChannelConfigKey<Boolean> http2AllowGracefulDelayed = new ChannelConfigKey<>("http2AllowGracefulDelayed", true);
    public static final ChannelConfigKey<Boolean> http2SwallowUnknownExceptionsOnConnClose = new ChannelConfigKey<>("http2SwallowUnknownExceptionsOnConnClose", false);
}
