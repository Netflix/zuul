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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.netflix.zuul.passport.PassportState.FILTERS_INBOUND_END;
import static com.netflix.zuul.passport.PassportState.FILTERS_INBOUND_START;
import static com.netflix.zuul.passport.PassportState.FILTERS_OUTBOUND_END;
import static com.netflix.zuul.passport.PassportState.FILTERS_OUTBOUND_START;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.netty.common.CloseOnIdleStateHandler;
import com.netflix.netty.common.Http1ConnectionCloseHandler;
import com.netflix.netty.common.Http1ConnectionExpiryHandler;
import com.netflix.netty.common.HttpRequestReadTimeoutHandler;
import com.netflix.netty.common.HttpServerLifecycleChannelHandler;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.accesslog.AccessLogChannelHandler;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.metrics.HttpBodySizeRecordingChannelHandler;
import com.netflix.netty.common.metrics.HttpMetricsChannelHandler;
import com.netflix.netty.common.metrics.PerEventLoopMetricsChannelHandler;
import com.netflix.netty.common.proxyprotocol.ElbProxyProtocolChannelHandler;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.throttle.MaxInboundConnectionsHandler;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.passport.InboundPassportStampingFilter;
import com.netflix.zuul.filters.passport.OutboundPassportStampingFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.filter.FilterRunner;
import com.netflix.zuul.netty.filter.ZuulEndPointRunner;
import com.netflix.zuul.netty.filter.ZuulFilterChainHandler;
import com.netflix.zuul.netty.filter.ZuulFilterChainRunner;
import com.netflix.zuul.netty.insights.PassportLoggingHandler;
import com.netflix.zuul.netty.insights.PassportStateHttpServerHandler;
import com.netflix.zuul.netty.insights.ServerStateHandler;
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
import io.netty.util.AttributeKey;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

/**
 * User: Mike Smith
 * Date: 3/5/16
 * Time: 6:26 PM
 */
public abstract class BaseZuulChannelInitializer extends ChannelInitializer<Channel>
{
    public static final String HTTP_CODEC_HANDLER_NAME = "codec";
    public static final AttributeKey<ChannelConfig> ATTR_CHANNEL_CONFIG = AttributeKey.newInstance("channel_config");

    protected static final LoggingHandler nettyLogger = new LoggingHandler("zuul.server.nettylog", LogLevel.INFO);

    public static final CachedDynamicIntProperty MAX_INITIAL_LINE_LENGTH = new CachedDynamicIntProperty("server.http.decoder.maxInitialLineLength", 16384);
    public static final CachedDynamicIntProperty MAX_HEADER_SIZE = new CachedDynamicIntProperty("server.http.decoder.maxHeaderSize", 32768);
    public static final CachedDynamicIntProperty MAX_CHUNK_SIZE = new CachedDynamicIntProperty("server.http.decoder.maxChunkSize", 32768);

    /**
     * The port that the server intends to listen on.  Subclasses should NOT use this field, as it may not be set, and
     * may differ from the actual listening port.  For example:
     *
     * <ul>
     *     <li>When binding the server to port `0`, the actual port will be different from the one provided here.
     *     <li>If there is no port (such as in a LocalSocket, or DomainSocket), the port number may be `-1`.
     * </ul>
     *
     * <p>Instead, subclasses should read the local address on channel initialization, and decide to take action then.
     */
    @Deprecated
    protected final int port;
    protected final String metricId;
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
    protected final Counter httpRequestReadTimeoutCounter;
    protected final FilterLoader filterLoader;
    protected final FilterUsageNotifier filterUsageNotifier;
    protected final SourceAddressChannelHandler sourceAddressChannelHandler;

    /** A collection of all the active channels that we can use to things like graceful shutdown */
    protected final ChannelGroup channels;

    /**
     * After calling this method, child classes should not reference {@link #port} any more.
     */
    protected BaseZuulChannelInitializer(
            String metricId,
            ChannelConfig channelConfig,
            ChannelConfig channelDependencies,
            ChannelGroup channels) {
        this(-1, metricId, channelConfig, channelDependencies, channels);
    }

    /**
     * Call {@link #BaseZuulChannelInitializer(String, ChannelConfig, ChannelConfig, ChannelGroup)} instead.
     */
    @Deprecated
    protected BaseZuulChannelInitializer(
            int port,
            ChannelConfig channelConfig,
            ChannelConfig channelDependencies,
            ChannelGroup channels) {
        this(port, String.valueOf(port), channelConfig, channelDependencies, channels);
    }

    private BaseZuulChannelInitializer(
            int port,
            String metricId,
            ChannelConfig channelConfig,
            ChannelConfig channelDependencies,
            ChannelGroup channels) {
        this.port = port;
        checkNotNull(metricId, "metricId");
        this.metricId = metricId;
        this.channelConfig = channelConfig;
        this.channelDependencies = channelDependencies;
        this.channels = channels;

        this.accessLogPublisher = channelDependencies.get(ZuulDependencyKeys.accessLogPublisher);

        this.withProxyProtocol = channelConfig.get(CommonChannelConfigKeys.withProxyProtocol);

        this.idleTimeout = channelConfig.get(CommonChannelConfigKeys.idleTimeout);
        this.httpRequestReadTimeout = channelConfig.get(CommonChannelConfigKeys.httpRequestReadTimeout);
        this.registry = channelDependencies.get(ZuulDependencyKeys.registry);
        this.httpMetricsHandler = new HttpMetricsChannelHandler(registry, "server", "http-" + metricId);

        EventLoopGroupMetrics eventLoopGroupMetrics = channelDependencies.get(ZuulDependencyKeys.eventLoopGroupMetrics);
        PerEventLoopMetricsChannelHandler perEventLoopMetricsHandler = new PerEventLoopMetricsChannelHandler(eventLoopGroupMetrics);
        this.perEventLoopConnectionMetricsHandler = perEventLoopMetricsHandler.new Connections();
        this.perEventLoopRequestsMetricsHandler = perEventLoopMetricsHandler.new HttpRequests();

        this.maxConnections = channelConfig.get(CommonChannelConfigKeys.maxConnections);
        this.maxConnectionsHandler = new MaxInboundConnectionsHandler(registry, metricId, maxConnections);
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

        this.sourceAddressChannelHandler = new SourceAddressChannelHandler();
    }

    protected void storeChannel(Channel ch)
    {
        this.channels.add(ch);

        // Also add the ChannelConfig as an attribute on each channel. So interested filters/channel-handlers can introspect
        // and potentially act differently based on the config.
        ch.attr(ATTR_CHANNEL_CONFIG).set(channelConfig);
    }

    protected void addPassportHandler(ChannelPipeline pipeline) {
        pipeline.addLast(new ServerStateHandler.InboundHandler(registry,"http-" + metricId));
        pipeline.addLast(new ServerStateHandler.OutboundHandler(registry));
    }
    
    protected void addTcpRelatedHandlers(ChannelPipeline pipeline)
    {
        pipeline.addLast(sourceAddressChannelHandler);
        pipeline.addLast(perEventLoopConnectionMetricsHandler);
        new ElbProxyProtocolChannelHandler(registry, withProxyProtocol).addProxyProtocol(pipeline);

        pipeline.addLast(maxConnectionsHandler);
    }

    protected void addHttp1Handlers(ChannelPipeline pipeline)
    {
        pipeline.addLast(HTTP_CODEC_HANDLER_NAME, createHttpServerCodec());

        pipeline.addLast(new Http1ConnectionCloseHandler());
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
        pipeline.addLast(new PassportStateHttpServerHandler.InboundHandler());
        pipeline.addLast(new PassportStateHttpServerHandler.OutboundHandler());
        if (httpRequestReadTimeout > -1) {
            HttpRequestReadTimeoutHandler.addLast(pipeline, httpRequestReadTimeout, TimeUnit.MILLISECONDS, httpRequestReadTimeoutCounter);
        }
        pipeline.addLast(new HttpServerLifecycleChannelHandler.HttpServerLifecycleInboundChannelHandler());
        pipeline.addLast(new HttpServerLifecycleChannelHandler.HttpServerLifecycleOutboundChannelHandler());
        pipeline.addLast(new HttpBodySizeRecordingChannelHandler.InboundChannelHandler());
        pipeline.addLast(new HttpBodySizeRecordingChannelHandler.OutboundChannelHandler());
        pipeline.addLast(httpMetricsHandler);
        pipeline.addLast(perEventLoopRequestsMetricsHandler);

        if (accessLogPublisher != null) {
            pipeline.addLast(new AccessLogChannelHandler.AccessLogInboundChannelHandler(accessLogPublisher));
            pipeline.addLast(new AccessLogChannelHandler.AccessLogOutboundChannelHandler());
        }

        pipeline.addLast(stripInboundProxyHeadersHandler);

        if (rateLimitingChannelHandler != null) {
            pipeline.addLast(rateLimitingChannelHandler);
        }

        //pipeline.addLast(requestRejectedChannelHandler);
    }

    protected void addTimeoutHandlers(ChannelPipeline pipeline) {
        pipeline.addLast(new IdleStateHandler(0, 0, idleTimeout, TimeUnit.MILLISECONDS));
        pipeline.addLast(new CloseOnIdleStateHandler(registry, metricId));
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
        return new ZuulEndPointRunner(filterUsageNotifier, filterLoader, responseFilterChain, registry);
    }

    protected <T extends ZuulMessage> ZuulFilterChainRunner<T> getFilterChainRunner(ZuulFilter<T, T>[] filters,
                                                                                    FilterUsageNotifier filterUsageNotifier) {
        return new ZuulFilterChainRunner<>(filters, filterUsageNotifier, registry);
    }

    protected <T extends ZuulMessage, R extends ZuulMessage> ZuulFilterChainRunner<T> getFilterChainRunner(ZuulFilter<T, T>[] filters,
                                                                                    FilterUsageNotifier filterUsageNotifier,
                                                                                    FilterRunner<T, R> filterRunner) {
        return new ZuulFilterChainRunner<>(filters, filterUsageNotifier, filterRunner, registry);
    }

    @SuppressWarnings("unchecked") // For the conversion from getFiltersByType.  It's not safe, sorry.
    public <T extends ZuulMessage> ZuulFilter<T, T> [] getFilters(ZuulFilter<T, T> start, ZuulFilter<T, T> stop) {
        final SortedSet<ZuulFilter<?, ?>> zuulFilters = filterLoader.getFiltersByType(start.filterType());
        final ZuulFilter<T, T>[] filters = new ZuulFilter[zuulFilters.size() + 2];
        filters[0] = start;
        int i = 1;
        for (ZuulFilter<?, ?> filter : zuulFilters) {
            // TODO(carl-mastrangelo): find some way to make this cast not needed.
            filters[i++] = (ZuulFilter<T, T>) filter;
        }
        filters[filters.length -1] = stop;
        return filters;
    }
}
