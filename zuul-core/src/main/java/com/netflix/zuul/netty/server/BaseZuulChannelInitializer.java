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

import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.netty.common.*;
import com.netflix.netty.common.accesslog.AccessLogChannelHandler;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.metrics.HttpBodySizeRecordingChannelHandler;
import com.netflix.netty.common.metrics.HttpMetricsChannelHandler;
import com.netflix.netty.common.metrics.PerEventLoopMetricsChannelHandler;
import com.netflix.netty.common.metrics.ServerChannelMetrics;
import com.netflix.netty.common.proxyprotocol.ElbProxyProtocolChannelHandler;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.status.ServerStatusHeaderHandler;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.netty.common.throttle.MaxInboundConnectionsHandler;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.insights.PassportLoggingHandler;
import com.netflix.zuul.netty.insights.PassportStateHttpServerHandler;
import com.netflix.zuul.netty.insights.PassportStateServerHandler;
import com.netflix.zuul.filters.passport.InboundPassportStampingFilter;
import com.netflix.zuul.filters.passport.OutboundPassportStampingFilter;
import com.netflix.zuul.netty.filter.FilterRunner;
import com.netflix.zuul.netty.filter.ZuulEndPointRunner;
import com.netflix.zuul.netty.filter.ZuulFilterChainHandler;
import com.netflix.zuul.netty.filter.ZuulFilterChainRunner;
import com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.netflix.zuul.passport.PassportState.FILTERS_INBOUND_END;
import static com.netflix.zuul.passport.PassportState.FILTERS_INBOUND_START;
import static com.netflix.zuul.passport.PassportState.FILTERS_OUTBOUND_END;
import static com.netflix.zuul.passport.PassportState.FILTERS_OUTBOUND_START;

/**
 * User: Mike Smith
 * Date: 3/5/16
 * Time: 6:26 PM
 */
public abstract class BaseZuulChannelInitializer extends ChannelInitializer<Channel>
{
    public static final String HTTP_CODEC_HANDLER_NAME = "codec";

    protected static final LoggingHandler nettyLogger = new LoggingHandler("zuul.server.nettylog", LogLevel.INFO);

    public static final CachedDynamicIntProperty MAX_INITIAL_LINE_LENGTH = new CachedDynamicIntProperty("server.http.decoder.maxInitialLineLength", 16384);
    public static final CachedDynamicIntProperty MAX_HEADER_SIZE = new CachedDynamicIntProperty("server.http.decoder.maxHeaderSize", 32768);
    public static final CachedDynamicIntProperty MAX_CHUNK_SIZE = new CachedDynamicIntProperty("server.http.decoder.maxChunkSize", 32768);

    protected final int port;
    protected final ChannelConfig channelConfig;
    protected final ChannelConfig channelDependencies;
    protected final int idleTimeout;
    protected final int httpRequestReadTimeout;
    protected final int maxRequestsPerConnection;
    protected final int maxRequestsPerConnectionInBrownout;
    protected final int connectionExpiry;
    protected final int maxConnections;
    private final int connCloseDelay;
    
    protected final Registry registry;
    protected final ServerChannelMetrics channelMetrics;
    protected final HttpMetricsChannelHandler httpMetricsHandler;
    protected final PerEventLoopMetricsChannelHandler.Connections perEventLoopConnectionMetricsHandler;
    protected final PerEventLoopMetricsChannelHandler.HttpRequests perEventLoopRequestsMetricsHandler;
    protected final MaxInboundConnectionsHandler maxConnectionsHandler;
    protected final AccessLogPublisher accessLogPublisher;
    protected final PassportLoggingHandler passportLoggingHandler;
    protected final boolean withProxyProtocol;
    protected final StripUntrustedProxyHeadersHandler stripInboundProxyHeadersHandler;
    // TODO
    //protected final HttpRequestThrottleChannelHandler requestThrottleHandler;
    protected final ChannelHandler rateLimitingChannelHandler;
    protected final ChannelHandler sslClientCertCheckChannelHandler;
    //protected final RequestRejectedChannelHandler requestRejectedChannelHandler;
    protected final SessionContextDecorator sessionContextDecorator;
    protected final RequestCompleteHandler requestCompleteHandler;
    protected final BasicCounter httpRequestReadTimeoutCounter;
    protected final FilterLoader filterLoader;
    protected final FilterUsageNotifier filterUsageNotifier;
    protected final ServerStatusHeaderHandler serverStatusHeaderHandler;

    /** A collection of all the active channels that we can use to things like graceful shutdown */
    protected final ChannelGroup channels; 


    protected BaseZuulChannelInitializer(
            int port,
            ChannelConfig channelConfig,
            ChannelConfig channelDependencies,
            ChannelGroup channels)
    {
        this.port = port;
        this.channelConfig = channelConfig;
        this.channelDependencies = channelDependencies;
        this.channels = channels;

        this.accessLogPublisher = channelDependencies.get(ZuulDependencyKeys.accessLogPublisher);

        this.withProxyProtocol = channelConfig.get(CommonChannelConfigKeys.withProxyProtocol);

        this.idleTimeout = channelConfig.get(CommonChannelConfigKeys.idleTimeout);
        this.httpRequestReadTimeout = channelConfig.get(CommonChannelConfigKeys.httpRequestReadTimeout);
        this.channelMetrics = new ServerChannelMetrics("http-" + port);
        this.registry = channelDependencies.get(ZuulDependencyKeys.registry);
        this.httpMetricsHandler = new HttpMetricsChannelHandler(registry, "server", "http-" + port);

        EventLoopGroupMetrics eventLoopGroupMetrics = channelDependencies.get(ZuulDependencyKeys.eventLoopGroupMetrics);
        PerEventLoopMetricsChannelHandler perEventLoopMetricsHandler = new PerEventLoopMetricsChannelHandler(eventLoopGroupMetrics);
        this.perEventLoopConnectionMetricsHandler = perEventLoopMetricsHandler.new Connections();
        this.perEventLoopRequestsMetricsHandler = perEventLoopMetricsHandler.new HttpRequests();

        this.maxConnections = channelConfig.get(CommonChannelConfigKeys.maxConnections);
        this.maxConnectionsHandler = new MaxInboundConnectionsHandler(maxConnections);
        this.maxRequestsPerConnection = channelConfig.get(CommonChannelConfigKeys.maxRequestsPerConnection);
        this.maxRequestsPerConnectionInBrownout = channelConfig.get(CommonChannelConfigKeys.maxRequestsPerConnectionInBrownout);
        this.connectionExpiry = channelConfig.get(CommonChannelConfigKeys.connectionExpiry);
        this.connCloseDelay = channelConfig.get(CommonChannelConfigKeys.connCloseDelay);

        StripUntrustedProxyHeadersHandler.AllowWhen allowProxyHeadersWhen = channelConfig.get(CommonChannelConfigKeys.allowProxyHeadersWhen);
        this.stripInboundProxyHeadersHandler = new StripUntrustedProxyHeadersHandler(allowProxyHeadersWhen);

        this.rateLimitingChannelHandler = channelDependencies.get(ZuulDependencyKeys.rateLimitingChannelHandlerProvider).get();

        this.sslClientCertCheckChannelHandler = channelDependencies.get(ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider).get();

        this.passportLoggingHandler = new PassportLoggingHandler(registry);

        this.sessionContextDecorator = channelDependencies.get(ZuulDependencyKeys.sessionCtxDecorator);
        this.requestCompleteHandler = channelDependencies.get(ZuulDependencyKeys.requestCompleteHandler);
        this.httpRequestReadTimeoutCounter = channelDependencies.get(ZuulDependencyKeys.httpRequestReadTimeoutCounter);

        this.filterLoader = channelDependencies.get(ZuulDependencyKeys.filterLoader);
        this.filterUsageNotifier = channelDependencies.get(ZuulDependencyKeys.filterUsageNotifier);

        ServerStatusManager serverStatusManager = channelDependencies.get(ZuulDependencyKeys.serverStatusManager);
        this.serverStatusHeaderHandler = new ServerStatusHeaderHandler(serverStatusManager);
    }

    protected void storeChannel(Channel ch)
    {
        this.channels.add(ch);
    }

    protected void addPassportHandler(ChannelPipeline pipeline)
    {
        pipeline.addLast(new PassportStateServerHandler());
    }
    
    protected void addTcpRelatedHandlers(ChannelPipeline pipeline)
    {
        pipeline.addLast(new SourceAddressChannelHandler());
        pipeline.addLast("channelMetrics", channelMetrics);
        pipeline.addLast(perEventLoopConnectionMetricsHandler);

        new ElbProxyProtocolChannelHandler(withProxyProtocol).addProxyProtocol(pipeline);

        pipeline.addLast(maxConnectionsHandler);
    }

    protected void addHttp1Handlers(ChannelPipeline pipeline)
    {
        pipeline.addLast(HTTP_CODEC_HANDLER_NAME, createHttpServerCodec());

        pipeline.addLast(new Http1ConnectionCloseHandler(connCloseDelay));
        pipeline.addLast("conn_expiry_handler",
                new Http1ConnectionExpiryHandler(maxRequestsPerConnection, maxRequestsPerConnectionInBrownout, connectionExpiry));
    }

    protected HttpServerCodec createHttpServerCodec()
    {
        return new HttpServerCodec(
                MAX_INITIAL_LINE_LENGTH.get(),
                MAX_HEADER_SIZE.get(),
                MAX_CHUNK_SIZE.get(),
                false
        );
    }
    
    protected void addHttpRelatedHandlers(ChannelPipeline pipeline)
    {
        pipeline.addLast(new PassportStateHttpServerHandler());
        if (httpRequestReadTimeout > -1) {
            HttpRequestReadTimeoutHandler.addLast(pipeline, httpRequestReadTimeout, TimeUnit.MILLISECONDS, httpRequestReadTimeoutCounter);
        }
        pipeline.addLast(new HttpServerLifecycleChannelHandler());
        pipeline.addLast(new HttpBodySizeRecordingChannelHandler());
        pipeline.addLast(httpMetricsHandler);
        pipeline.addLast(perEventLoopRequestsMetricsHandler);

        if (accessLogPublisher != null) {
            pipeline.addLast(new AccessLogChannelHandler(accessLogPublisher));
        }

        pipeline.addLast(serverStatusHeaderHandler);
        //pipeline.addLast(requestThrottleHandler);
        pipeline.addLast(stripInboundProxyHeadersHandler);

        if (rateLimitingChannelHandler != null) {
            pipeline.addLast(rateLimitingChannelHandler);
        }

        //pipeline.addLast(requestRejectedChannelHandler);
    }

    protected void addTimeoutHandlers(ChannelPipeline pipeline) {
        pipeline.addLast(new IdleStateHandler(0, 0, idleTimeout, TimeUnit.MILLISECONDS));
        pipeline.addLast(new CloseOnIdleStateHandler());
    }

    protected void addSslInfoHandlers(ChannelPipeline pipeline, boolean isSSlFromIntermediary) {
        pipeline.addLast("ssl_info", new SslHandshakeInfoHandler(registry, isSSlFromIntermediary));
    }

    protected void addSslClientCertChecks(ChannelPipeline pipeline)
    {
        if (channelConfig.get(ZuulDependencyKeys.SSL_CLIENT_CERT_CHECK_REQUIRED)) {
            if (this.sslClientCertCheckChannelHandler == null) {
                throw new IllegalArgumentException("A sslClientCertCheckChannelHandler is required!");
            }
            pipeline.addLast(this.sslClientCertCheckChannelHandler);
        }
    }

    protected void addZuulHandlers(final ChannelPipeline pipeline)
    {
        pipeline.addLast("logger", nettyLogger);
        pipeline.addLast(new ClientRequestReceiver(sessionContextDecorator));
        pipeline.addLast(passportLoggingHandler);
        addZuulFilterChainHandler(pipeline);
        pipeline.addLast(new ClientResponseWriter(requestCompleteHandler, registry));
    }

    protected void addZuulFilterChainHandler(final ChannelPipeline pipeline) {
        final ZuulFilter<HttpResponseMessage, HttpResponseMessage>[] responseFilters = getFilters(
                new OutboundPassportStampingFilter(FILTERS_OUTBOUND_START),
                new OutboundPassportStampingFilter(FILTERS_OUTBOUND_END));

        // response filter chain
        final ZuulFilterChainRunner<HttpResponseMessage> responseFilterChain = getFilterChainRunner(responseFilters,
                filterUsageNotifier);

        // endpoint | response filter chain
        final FilterRunner<HttpRequestMessage, HttpResponseMessage> endPoint = getEndpointRunner(responseFilterChain,
                filterUsageNotifier, filterLoader);

        final ZuulFilter<HttpRequestMessage, HttpRequestMessage>[] requestFilters = getFilters(
                new InboundPassportStampingFilter(FILTERS_INBOUND_START),
                new InboundPassportStampingFilter(FILTERS_INBOUND_END));

        // request filter chain | end point | response filter chain
        final ZuulFilterChainRunner<HttpRequestMessage> requestFilterChain = getFilterChainRunner(requestFilters,
                filterUsageNotifier, endPoint);

        pipeline.addLast(new ZuulFilterChainHandler(requestFilterChain, responseFilterChain));
    }

    protected ZuulEndPointRunner getEndpointRunner(ZuulFilterChainRunner<HttpResponseMessage> responseFilterChain,
                                                   FilterUsageNotifier filterUsageNotifier, FilterLoader filterLoader) {
        return new ZuulEndPointRunner(filterUsageNotifier, filterLoader, responseFilterChain);
    }

    protected <T extends ZuulMessage> ZuulFilterChainRunner<T> getFilterChainRunner(ZuulFilter<T, T>[] filters,
                                                                                    FilterUsageNotifier filterUsageNotifier) {
        return new ZuulFilterChainRunner<>(filters, filterUsageNotifier);
    }

    protected <T extends ZuulMessage, R extends ZuulMessage> ZuulFilterChainRunner<T> getFilterChainRunner(ZuulFilter<T, T>[] filters,
                                                                                    FilterUsageNotifier filterUsageNotifier,
                                                                                    FilterRunner<T, R> filterRunner) {
        return new ZuulFilterChainRunner<>(filters, filterUsageNotifier, filterRunner);
    }

    public <T extends ZuulMessage> ZuulFilter<T, T> [] getFilters(final ZuulFilter start, final ZuulFilter stop) {
        final List<ZuulFilter> zuulFilters = filterLoader.getFiltersByType(start.filterType());
        final ZuulFilter[] filters = new ZuulFilter[zuulFilters.size() + 2];
        filters[0] = start;
        for (int i=1, j=0; i < filters.length && j < zuulFilters.size(); i++,j++) {
            filters[i] = zuulFilters.get(j);
        }
        filters[filters.length -1] = stop;
        return filters;
    }

}
