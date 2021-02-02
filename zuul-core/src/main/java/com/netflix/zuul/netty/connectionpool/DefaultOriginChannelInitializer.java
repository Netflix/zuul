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

package com.netflix.zuul.netty.connectionpool;

import com.netflix.netty.common.HttpClientLifecycleChannelHandler;
import com.netflix.netty.common.metrics.HttpMetricsChannelHandler;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.insights.PassportStateHttpClientHandler;
import com.netflix.zuul.netty.insights.PassportStateOriginHandler;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import com.netflix.zuul.netty.ssl.ClientSslContextFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;

import static com.netflix.zuul.netty.server.BaseZuulChannelInitializer.HTTP_CODEC_HANDLER_NAME;

/**
 * Default Origin Channel Initializer
 *
 * Author: Arthur Gonigberg
 * Date: December 01, 2017
 */
public class DefaultOriginChannelInitializer extends OriginChannelInitializer {
    private final ConnectionPoolConfig connectionPoolConfig;
    private final SslContext sslContext;
    protected final ConnectionPoolHandler connectionPoolHandler;
    protected final HttpMetricsChannelHandler httpMetricsHandler;
    protected final LoggingHandler nettyLogger;

    public DefaultOriginChannelInitializer(ConnectionPoolConfig connPoolConfig, Registry spectatorRegistry) {
        this.connectionPoolConfig = connPoolConfig;
        String niwsClientName = connectionPoolConfig.getOriginName().getNiwsClientName();
        this.connectionPoolHandler = new ConnectionPoolHandler(connectionPoolConfig.getOriginName());
        this.httpMetricsHandler = new HttpMetricsChannelHandler(spectatorRegistry, "client", niwsClientName);
        this.nettyLogger = new LoggingHandler("zuul.origin.nettylog." + niwsClientName, LogLevel.INFO);
        this.sslContext = getClientSslContext(spectatorRegistry);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        final ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new PassportStateOriginHandler.InboundHandler());
        pipeline.addLast(new PassportStateOriginHandler.OutboundHandler());

        if (connectionPoolConfig.isSecure()) {
            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
        }

        pipeline.addLast(HTTP_CODEC_HANDLER_NAME, new HttpClientCodec(
                BaseZuulChannelInitializer.MAX_INITIAL_LINE_LENGTH.get(),
                BaseZuulChannelInitializer.MAX_HEADER_SIZE.get(),
                BaseZuulChannelInitializer.MAX_CHUNK_SIZE.get(),
                false,
                false
        ));
        pipeline.addLast(new PassportStateHttpClientHandler.InboundHandler());
        pipeline.addLast(new PassportStateHttpClientHandler.OutboundHandler());
        pipeline.addLast("originNettyLogger", nettyLogger);
        pipeline.addLast(httpMetricsHandler);
        addMethodBindingHandler(pipeline);
        pipeline.addLast(HttpClientLifecycleChannelHandler.INBOUND_CHANNEL_HANDLER);
        pipeline.addLast(HttpClientLifecycleChannelHandler.OUTBOUND_CHANNEL_HANDLER);
        pipeline.addLast(new ClientTimeoutHandler.InboundHandler());
        pipeline.addLast(new ClientTimeoutHandler.OutboundHandler());
        pipeline.addLast("connectionPoolHandler", connectionPoolHandler);
    }

    /**
     * This method can be overridden to create your own custom SSL context
     *
     * @param spectatorRegistry metrics registry
     * @return Netty SslContext
     */
    protected SslContext getClientSslContext(Registry spectatorRegistry) {
        return new ClientSslContextFactory(spectatorRegistry).getClientSslContext();
    }

    /**
     * This method can be overridden to add your own MethodBinding handler for preserving thread locals or thread variables.
     *
     * This should be a handler that binds downstream channelRead and userEventTriggered with the
     * MethodBinding class. It should be added using the pipeline.addLast method.
     *
     * @param pipeline the channel pipeline
     */
    protected void addMethodBindingHandler(ChannelPipeline pipeline) {
    }

    public HttpMetricsChannelHandler getHttpMetricsHandler() {
        return httpMetricsHandler;
    }
}

