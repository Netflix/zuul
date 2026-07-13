/*
 * Copyright 2026 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.zuul.netty.server.Server;
import com.netflix.zuul.origins.OriginName;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Justin Guerra
 * @since 7/10/26
 */
class NettyClientConnectionFactoryTest {

    private static Class<? extends Channel> previousChannelType;

    private DefaultClientConfigImpl clientConfig;
    private NettyClientConnectionFactory factory;
    private EventLoop eventLoop;
    private IConnectionPool pool;

    @BeforeAll
    static void setupChannelType() {
        previousChannelType = Server.defaultOutboundChannelType.getAndSet(NioSocketChannel.class);
    }

    @AfterAll
    static void restoreChannelType() {
        if (previousChannelType != null) {
            Server.defaultOutboundChannelType.set(previousChannelType);
        }
    }

    @BeforeEach
    void setup() {
        clientConfig = DefaultClientConfigImpl.getEmptyConfig();
        ConnectionPoolConfig connPoolConfig =
                new ConnectionPoolConfigImpl(OriginName.fromVipAndApp("whatever-secure", "whatever"), clientConfig);
        factory = new NettyClientConnectionFactory(connPoolConfig, new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {}
        });
        eventLoop = new DefaultEventLoop();
        pool = mock(IConnectionPool.class);
    }

    @AfterEach
    void cleanup() {
        eventLoop.shutdownGracefully();
    }

    @Test
    void bootstrapAppliesConfiguredChannelOptions() {
        Bootstrap bootstrap = factory.createBootstrap(eventLoop, originAddress(), CurrentPassport.create(), pool);

        Map<ChannelOption<?>, Object> options = bootstrap.config().options();
        assertThat(options)
                .containsEntry(ChannelOption.CONNECT_TIMEOUT_MILLIS, ConnectionPoolConfigImpl.DEFAULT_CONNECT_TIMEOUT)
                .containsEntry(ChannelOption.SO_KEEPALIVE, false)
                .containsEntry(ChannelOption.TCP_NODELAY, true)
                .containsEntry(ChannelOption.AUTO_READ, false)
                .containsEntry(ChannelOption.SO_SNDBUF, ConnectionPoolConfigImpl.DEFAULT_BUFFER_SIZE)
                .containsEntry(ChannelOption.SO_RCVBUF, ConnectionPoolConfigImpl.DEFAULT_BUFFER_SIZE);

        WriteBufferWaterMark waterMark = (WriteBufferWaterMark) options.get(ChannelOption.WRITE_BUFFER_WATER_MARK);
        assertThat(waterMark.low()).isEqualTo(ConnectionPoolConfigImpl.DEFAULT_WRITE_BUFFER_LOW_WATER_MARK);
        assertThat(waterMark.high()).isEqualTo(ConnectionPoolConfigImpl.DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK);

        assertThat(bootstrap.config().attrs()).containsKey(CurrentPassport.CHANNEL_ATTR);
    }

    @Test
    void bootstrapAppliesOverriddenSocketBufferSizes() {
        clientConfig.set(IClientConfigKey.Keys.ReceiveBufferSize, 40000);
        clientConfig.set(IClientConfigKey.Keys.SendBufferSize, 50000);

        Bootstrap bootstrap = factory.createBootstrap(eventLoop, originAddress(), CurrentPassport.create(), pool);

        assertThat(bootstrap.config().options())
                .containsEntry(ChannelOption.SO_RCVBUF, 40000)
                .containsEntry(ChannelOption.SO_SNDBUF, 50000);
    }

    @Test
    void bootstrapOmitsSocketBufferOptionsWhenUsingDefault() {
        clientConfig.set(ConnectionPoolConfigImpl.USE_DEFAULT_TCP_BUFFER_SIZES, true);

        Bootstrap bootstrap = factory.createBootstrap(eventLoop, originAddress(), CurrentPassport.create(), pool);

        assertThat(bootstrap.config().options())
                .doesNotContainKey(ChannelOption.SO_SNDBUF)
                .doesNotContainKey(ChannelOption.SO_RCVBUF)
                .containsEntry(ChannelOption.TCP_NODELAY, true);
    }

    private static InetSocketAddress originAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 7001);
    }
}
