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

import static com.google.common.base.Preconditions.checkNotNull;

import com.netflix.netty.common.Http2ConnectionCloseHandler;
import com.netflix.netty.common.Http2ConnectionExpiryHandler;
import com.netflix.netty.common.SwallowSomeHttp2ExceptionsHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.Http2MetricsChannelHandlers;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.zuul.logging.Http2FrameLoggingPerClientIpHandler;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import com.netflix.zuul.netty.ssl.SslContextFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Mike Smith
 * Date: 3/5/16
 * Time: 5:41 PM
 */
public final class Http2SslChannelInitializer extends BaseZuulChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(Http2SslChannelInitializer.class);
    private static final DummyChannelHandler DUMMY_HANDLER = new DummyChannelHandler();

    private final ServerSslConfig serverSslConfig;
    private final SslContext sslContext;
    private final boolean isSSlFromIntermediary;
    private final SwallowSomeHttp2ExceptionsHandler swallowSomeHttp2ExceptionsHandler;
    private final String metricId;


    /**
     * Use {@link #Http2SslChannelInitializer(String, ChannelConfig, ChannelConfig, ChannelGroup)} instead.
     */
    @Deprecated
    public Http2SslChannelInitializer(int port,
                                      ChannelConfig channelConfig,
                                      ChannelConfig channelDependencies,
                                      ChannelGroup channels) {
        this(String.valueOf(port), channelConfig, channelDependencies, channels);
    }

    public Http2SslChannelInitializer(String metricId,
                                      ChannelConfig channelConfig,
                                      ChannelConfig channelDependencies,
                                      ChannelGroup channels) {
        super(metricId, channelConfig, channelDependencies, channels);
        this.metricId = checkNotNull(metricId, "metricId");

        this.swallowSomeHttp2ExceptionsHandler = new SwallowSomeHttp2ExceptionsHandler(registry);

        this.serverSslConfig = channelConfig.get(CommonChannelConfigKeys.serverSslConfig);
        this.isSSlFromIntermediary = channelConfig.get(CommonChannelConfigKeys.isSSlFromIntermediary);

        SslContextFactory sslContextFactory = channelConfig.get(CommonChannelConfigKeys.sslContextFactory);
        sslContext = Http2Configuration.configureSSL(sslContextFactory, metricId);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        SslHandler sslHandler = sslContext.newHandler(ch.alloc());
        sslHandler.engine().setEnabledProtocols(serverSslConfig.getProtocols());

//        SSLParameters sslParameters = new SSLParameters();
//        AlgorithmConstraints algoConstraints = new AlgorithmConstraints();
//        sslParameters.setAlgorithmConstraints(algoConstraints);
//        sslParameters.setUseCipherSuitesOrder(true);
//        sslHandler.engine().setSSLParameters(sslParameters);

        if (LOG.isDebugEnabled()) {
            LOG.debug("ssl protocols supported: {}", String.join(", ", sslHandler.engine().getSupportedProtocols()));
            LOG.debug("ssl protocols enabled: {}", String.join(", ", sslHandler.engine().getEnabledProtocols()));

            LOG.debug("ssl ciphers supported: {}", String.join(", ", sslHandler.engine().getSupportedCipherSuites()));
            LOG.debug("ssl ciphers enabled: {}", String.join(", ", sslHandler.engine().getEnabledCipherSuites()));
        }

        // Configure our pipeline of ChannelHandlerS.
        ChannelPipeline pipeline = ch.pipeline();

        storeChannel(ch);
        addTimeoutHandlers(pipeline);
        addPassportHandler(pipeline);
        addTcpRelatedHandlers(pipeline);
        pipeline.addLast(new Http2FrameLoggingPerClientIpHandler());
        pipeline.addLast("ssl", sslHandler);
        addSslInfoHandlers(pipeline, isSSlFromIntermediary);
        addSslClientCertChecks(pipeline);

        Http2MetricsChannelHandlers http2MetricsChannelHandlers =
                new Http2MetricsChannelHandlers(registry,"server", "http2-" + metricId);

        Http2ConnectionCloseHandler connectionCloseHandler = new Http2ConnectionCloseHandler(registry);
        Http2ConnectionExpiryHandler connectionExpiryHandler = new Http2ConnectionExpiryHandler(maxRequestsPerConnection, maxRequestsPerConnectionInBrownout, connectionExpiry);

        pipeline.addLast("http2CodecSwapper", new Http2OrHttpHandler(
                new Http2StreamInitializer(ch, this::http1Handlers, http2MetricsChannelHandlers, connectionCloseHandler, connectionExpiryHandler),
                channelConfig,
                cp -> {
                    http1Codec(cp);
                    http1Handlers(cp);
                }));
        pipeline.addLast("codec_placeholder", DUMMY_HANDLER);

        pipeline.addLast(swallowSomeHttp2ExceptionsHandler);
    }

    protected void http1Handlers(ChannelPipeline pipeline) {
        addHttpRelatedHandlers(pipeline);
        addZuulHandlers(pipeline);
    }

    protected void http1Codec(ChannelPipeline pipeline) {
        pipeline.replace("codec_placeholder", HTTP_CODEC_HANDLER_NAME, createHttpServerCodec());
    }
}

