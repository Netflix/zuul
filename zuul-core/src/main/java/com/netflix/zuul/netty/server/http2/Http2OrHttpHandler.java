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

package com.netflix.zuul.netty.server.http2;

import com.netflix.netty.common.Http2ConnectionCloseHandler;
import com.netflix.netty.common.Http2ConnectionExpiryHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.Http2MetricsChannelHandlers;
import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.util.AttributeKey;

import java.util.function.Consumer;

import static com.netflix.zuul.netty.server.BaseZuulChannelInitializer.HTTP_CODEC_HANDLER_NAME;

/**
 * Http2 Or Http Handler
 *
 * Author: Arthur Gonigberg
 * Date: December 15, 2017
 */
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {
    public static final AttributeKey<String> PROTOCOL_NAME = AttributeKey.valueOf("protocol_name");
    public static final AttributeKey<Http2Connection> H2_CONN_KEY = AttributeKey.newInstance("h2_connection");

    private final ChannelHandler http2StreamHandler;
    private final int maxConcurrentStreams;
    private final int initialWindowSize;
    private final long maxHeaderTableSize;
    private final long maxHeaderListSize;
    private final Http2MetricsChannelHandlers http2MetricsChannelHandlers;
    private final int maxRequestsPerConnection;
    private final int maxExpiry;
    private final Http2ConnectionCloseHandler connectionCloseHandler;
    private int maxRequestsPerConnectionInBrownout;
    private final Consumer<ChannelPipeline> addHttpHandlerFn;

    public Http2OrHttpHandler(ChannelHandler http2StreamHandler, ChannelConfig channelConfig, Registry spectatorRegistry,
                              int port, int maxRequestsPerConnectionInBrownout, Consumer<ChannelPipeline> addHttpHandlerFn) {
        super(ApplicationProtocolNames.HTTP_1_1);
        this.http2StreamHandler = http2StreamHandler;
        this.maxConcurrentStreams = channelConfig.get(CommonChannelConfigKeys.maxConcurrentStreams);
        this.initialWindowSize = channelConfig.get(CommonChannelConfigKeys.initialWindowSize);
        this.maxRequestsPerConnection = channelConfig.get(CommonChannelConfigKeys.maxRequestsPerConnection);
        this.maxExpiry = channelConfig.get(CommonChannelConfigKeys.connectionExpiry);
        this.maxHeaderTableSize = channelConfig.get(CommonChannelConfigKeys.maxHttp2HeaderTableSize);
        this.maxHeaderListSize = channelConfig.get(CommonChannelConfigKeys.maxHttp2HeaderListSize);
        this.maxRequestsPerConnectionInBrownout = maxRequestsPerConnectionInBrownout;
        this.addHttpHandlerFn = addHttpHandlerFn;

        this.http2MetricsChannelHandlers = new Http2MetricsChannelHandlers(spectatorRegistry, "server", "http2-" + port);

        int connCloseDelay = channelConfig.get(CommonChannelConfigKeys.connCloseDelay);
        connectionCloseHandler = new Http2ConnectionCloseHandler(connCloseDelay);
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            ctx.channel().attr(PROTOCOL_NAME).set("HTTP/2");
            configureHttp2(ctx.pipeline());
            return;
        }
        if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            ctx.channel().attr(PROTOCOL_NAME).set("HTTP/1.1");
            configureHttp1(ctx.pipeline());
            return;
        }

        throw new IllegalStateException("unknown protocol: " + protocol);
    }

    private void configureHttp2(ChannelPipeline pipeline) {

        // setup the initial stream settings for the server to use.
        Http2Settings settings = new Http2Settings()
                .maxConcurrentStreams(maxConcurrentStreams)
                .initialWindowSize(initialWindowSize)
                .headerTableSize(maxHeaderTableSize)
                .maxHeaderListSize(maxHeaderListSize);

        Http2MultiplexCodec multiplexCodec = Http2MultiplexCodecBuilder
                .forServer(http2StreamHandler)
                .initialSettings(settings)
                .validateHeaders(false)
                .build();
        pipeline.replace("codec_placeholder", HTTP_CODEC_HANDLER_NAME, multiplexCodec);

        // Need to pass the connection to our handler later, so store it on channel now.
        Http2Connection connection = multiplexCodec.connection();
        pipeline.channel().attr(H2_CONN_KEY).set(connection);

        // TODO - These handlers might need to be juggled around now that only using the multiplex codec.
        pipeline.addAfter(HTTP_CODEC_HANDLER_NAME, "h2_metrics_inbound", http2MetricsChannelHandlers.inbound());
        pipeline.addAfter(HTTP_CODEC_HANDLER_NAME, "h2_metrics_outbound", http2MetricsChannelHandlers.outbound());
        pipeline.addAfter(HTTP_CODEC_HANDLER_NAME, "h2_max_requests_per_conn",
                new Http2ConnectionExpiryHandler(maxRequestsPerConnection, maxRequestsPerConnectionInBrownout, maxExpiry));
        pipeline.addAfter("h2_max_requests_per_conn", "h2_conn_close", connectionCloseHandler);

    }

    private void configureHttp1(ChannelPipeline pipeline) {
        addHttpHandlerFn.accept(pipeline);
    }
}
