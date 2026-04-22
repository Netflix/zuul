/*
 * Copyright 2019 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.Http1ConnectionCloseHandler;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.PerEventLoopMetricsChannelHandler;
import com.netflix.netty.common.proxyprotocol.ElbProxyProtocolChannelHandler;
import com.netflix.netty.common.throttle.MaxInboundConnectionsHandler;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.zuul.netty.insights.ServerStateHandler;
import com.netflix.zuul.netty.ratelimiting.NullChannelHandlerProvider;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BaseZuulChannelInitializer}.
 */
class BaseZuulChannelInitializerTest {

    @AfterEach
    void resetProperties() {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.clearProperty("zuul.http1.framing.enforcement.enabled");
    }

    @Test
    void tcpHandlersAdded() {
        ChannelConfig channelConfig = new ChannelConfig();
        ChannelConfig channelDependencies = new ChannelConfig();
        channelDependencies.set(ZuulDependencyKeys.registry, new NoopRegistry());
        channelDependencies.set(
                ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDependencies.set(
                ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());
        ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        BaseZuulChannelInitializer init =
                new BaseZuulChannelInitializer("1234", channelConfig, channelDependencies, channelGroup) {

                    @Override
                    protected void initChannel(Channel ch) {}
                };
        EmbeddedChannel channel = new EmbeddedChannel();

        init.addTcpRelatedHandlers(channel.pipeline());

        assertThat(channel.pipeline().context(SourceAddressChannelHandler.class))
                .isNotNull();
        assertThat(channel.pipeline().context(PerEventLoopMetricsChannelHandler.Connections.class))
                .isNotNull();
        assertThat(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME))
                .isNotNull();
        assertThat(channel.pipeline().context(MaxInboundConnectionsHandler.class))
                .isNotNull();
    }

    @Test
    void tcpHandlersAdded_withProxyProtocol() {
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, true);
        ChannelConfig channelDependencies = new ChannelConfig();
        channelDependencies.set(ZuulDependencyKeys.registry, new NoopRegistry());
        channelDependencies.set(
                ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDependencies.set(
                ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());
        ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        BaseZuulChannelInitializer init =
                new BaseZuulChannelInitializer("1234", channelConfig, channelDependencies, channelGroup) {

                    @Override
                    protected void initChannel(Channel ch) {}
                };
        EmbeddedChannel channel = new EmbeddedChannel();

        init.addTcpRelatedHandlers(channel.pipeline());

        assertThat(channel.pipeline().context(SourceAddressChannelHandler.class))
                .isNotNull();
        assertThat(channel.pipeline().context(PerEventLoopMetricsChannelHandler.Connections.class))
                .isNotNull();
        assertThat(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME))
                .isNotNull();
        assertThat(channel.pipeline().context(MaxInboundConnectionsHandler.class))
                .isNotNull();
    }

    @Test
    void serverStateHandlerAdded() {
        ChannelConfig channelConfig = new ChannelConfig();
        ChannelConfig channelDependencies = new ChannelConfig();
        channelDependencies.set(ZuulDependencyKeys.registry, new NoopRegistry());
        channelDependencies.set(
                ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDependencies.set(
                ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());

        ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        BaseZuulChannelInitializer init =
                new BaseZuulChannelInitializer("1234", channelConfig, channelDependencies, channelGroup) {

                    @Override
                    protected void initChannel(Channel ch) {}
                };
        EmbeddedChannel channel = new EmbeddedChannel();

        init.addPassportHandler(channel.pipeline());

        assertThat(channel.pipeline().context(ServerStateHandler.InboundHandler.class))
                .isNotNull();
        assertThat(channel.pipeline().context(ServerStateHandler.OutboundHandler.class))
                .isNotNull();
    }

    @Test
    void http1FramingEnforcerRunsBetweenCodecAndConnectionCloseHandler() {
        ChannelConfig channelConfig = new ChannelConfig();
        ChannelConfig channelDependencies = new ChannelConfig();
        channelDependencies.set(ZuulDependencyKeys.registry, new NoopRegistry());
        channelDependencies.set(
                ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDependencies.set(
                ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());

        ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        BaseZuulChannelInitializer init =
                new BaseZuulChannelInitializer("1234", channelConfig, channelDependencies, channelGroup) {
                    @Override
                    protected void initChannel(Channel ch) {}
                };
        EmbeddedChannel channel = new EmbeddedChannel();

        init.addHttp1Handlers(channel.pipeline());
        assertThat(channel.pipeline().context(Http1FramingEnforcingHandler.class))
                .as("Http1FramingEnforcingHandler must be wired into the HTTP/1.1 pipeline")
                .isNotNull();

        List<Class<? extends ChannelHandler>> handlerClasses = StreamSupport.stream(
                        channel.pipeline().spliterator(), false)
                .map(entry -> entry.getValue().getClass())
                .collect(Collectors.toList());

        int codecIndex = handlerClasses.indexOf(HttpServerCodec.class);
        int enforcerIndex = handlerClasses.indexOf(Http1FramingEnforcingHandler.class);
        int closeHandlerIndex = handlerClasses.indexOf(Http1ConnectionCloseHandler.class);

        assertThat(codecIndex).as("HttpServerCodec must be present").isNotNegative();
        assertThat(closeHandlerIndex)
                .as("Http1ConnectionCloseHandler must be present")
                .isNotNegative();
        assertThat(enforcerIndex)
                .as("Http1FramingEnforcingHandler must run after HttpServerCodec")
                .isGreaterThan(codecIndex);
        assertThat(enforcerIndex)
                .as("Http1FramingEnforcingHandler must run before Http1ConnectionCloseHandler")
                .isLessThan(closeHandlerIndex);
    }

    @Test
    void http1FramingEnforcerIsOmittedWhenDisabled() {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("zuul.http1.framing.enforcement.enabled", "false");

        ChannelConfig channelConfig = new ChannelConfig();
        ChannelConfig channelDependencies = new ChannelConfig();
        channelDependencies.set(ZuulDependencyKeys.registry, new NoopRegistry());
        channelDependencies.set(
                ZuulDependencyKeys.rateLimitingChannelHandlerProvider, new NullChannelHandlerProvider());
        channelDependencies.set(
                ZuulDependencyKeys.sslClientCertCheckChannelHandlerProvider, new NullChannelHandlerProvider());
        ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        BaseZuulChannelInitializer init =
                new BaseZuulChannelInitializer("1234", channelConfig, channelDependencies, channelGroup) {
                    @Override
                    protected void initChannel(Channel ch) {}
                };
        EmbeddedChannel channel = new EmbeddedChannel();

        init.addHttp1Handlers(channel.pipeline());

        assertThat(channel.pipeline().context(Http1FramingEnforcingHandler.class))
                .as("Http1FramingEnforcingHandler must not be wired when the killswitch is disabled")
                .isNull();
        assertThat(channel.pipeline().context(HttpServerCodec.class))
                .as("HttpServerCodec is still wired even when the enforcement handler is off")
                .isNotNull();
        assertThat(channel.pipeline().context(Http1ConnectionCloseHandler.class))
                .as("Http1ConnectionCloseHandler is still wired even when the enforcement handler is off")
                .isNotNull();
    }
}
