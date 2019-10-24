package com.netflix.zuul.netty.server;

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.PerEventLoopMetricsChannelHandler;
import com.netflix.netty.common.metrics.ServerChannelMetrics;
import com.netflix.netty.common.proxyprotocol.ElbProxyProtocolChannelHandler;
import com.netflix.netty.common.proxyprotocol.OptionalHAProxyMessageDecoder;
import com.netflix.netty.common.throttle.MaxInboundConnectionsHandler;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.zuul.netty.ratelimiting.NullChannelHandlerProvider;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link BaseZuulChannelInitializer}.
 */
@RunWith(JUnit4.class)
public class BaseZuulChannelInitializerTest {

    @Test
    public void tcpHandlersAdded() {
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

        assertNotNull(channel.pipeline().context(SourceAddressChannelHandler.class));
        assertNotNull(channel.pipeline().context(ServerChannelMetrics.class));
        assertNotNull(channel.pipeline().context(PerEventLoopMetricsChannelHandler.Connections.class));
        assertNotNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertNull(channel.pipeline().context(OptionalHAProxyMessageDecoder.NAME));
        assertNotNull(channel.pipeline().context(MaxInboundConnectionsHandler.class));
    }

    @Test
    public void tcpHandlersAdded_withProxyProtocol() {
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

        assertNotNull(channel.pipeline().context(SourceAddressChannelHandler.class));
        assertNotNull(channel.pipeline().context(ServerChannelMetrics.class));
        assertNotNull(channel.pipeline().context(PerEventLoopMetricsChannelHandler.Connections.class));
        assertNotNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertNotNull(channel.pipeline().context(OptionalHAProxyMessageDecoder.NAME));
        assertNotNull(channel.pipeline().context(MaxInboundConnectionsHandler.class));
    }
}
