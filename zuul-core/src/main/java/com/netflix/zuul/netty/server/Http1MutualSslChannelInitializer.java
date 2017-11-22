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

import com.netflix.zuul.netty.ssl.SslContextFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;

import javax.net.ssl.SSLException;

/**
 * User: michaels@netflix.com
 * Date: 1/31/17
 * Time: 11:43 PM
 */
public class Http1MutualSslChannelInitializer extends BaseZuulChannelInitializer
{
    private final SslContextFactory sslContextFactory;
    private final SslContext sslContext;
    private final boolean isSSlFromIntermediary;

    public Http1MutualSslChannelInitializer(
                                            int port,
                                            ChannelConfig channelConfig,
                                            ChannelConfig channelDependencies,
                                            ChannelGroup channels)
    {
        super(port, channelConfig, channelDependencies, channels);

        this.isSSlFromIntermediary = channelConfig.get(CommonChannelConfigKeys.isSSlFromIntermediary);

        this.sslContextFactory = channelConfig.get(CommonChannelConfigKeys.sslContextFactory);
        try {
            sslContext = sslContextFactory.createBuilderForServer().build();
        }
        catch (SSLException e) {
            throw new RuntimeException("Error configuring SslContext!", e);
        }

        // Enable TLS Session Tickets support.
        sslContextFactory.enableSessionTickets(sslContext);

        // Setup metrics tracking the OpenSSL stats.
        sslContextFactory.configureOpenSslStatsMetrics(sslContext, Integer.toString(port));
    }

    @Override
    protected void initChannel(Channel ch) throws Exception
    {
        SslHandler sslHandler = sslContext.newHandler(ch.alloc());
        sslHandler.engine().setEnabledProtocols(sslContextFactory.getProtocols());

        // Configure our pipeline of ChannelHandlerS.
        ChannelPipeline pipeline = ch.pipeline();

        storeChannel(ch);
        addTimeoutHandlers(pipeline);
        addPassportHandler(pipeline);
        addTcpRelatedHandlers(pipeline);
        pipeline.addLast("ssl", sslHandler);
        addSslInfoHandlers(pipeline, isSSlFromIntermediary);
        addSslClientCertChecks(pipeline);
        addHttp1Handlers(pipeline);
        addHttpRelatedHandlers(pipeline);
        addZuulHandlers(pipeline);
    }
}
