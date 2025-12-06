/*
 * Copyright 2020 Netflix, Inc.
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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Spectator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link Server}.
 */
@SuppressWarnings("AddressSelection")
class ServerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTest.class);

    @BeforeEach
    void beforeTest() {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("zuul.server.netty.socket.force_nio", "true");
        config.setProperty("zuul.server.netty.socket.force_io_uring", "false");
    }

    @Test
    void getListeningSockets() throws Exception {
        ServerStatusManager ssm = mock(ServerStatusManager.class);
        Map<NamedSocketAddress, ChannelInitializer<?>> initializers = new HashMap<>();
        List<NioSocketChannel> nioChannels = Collections.synchronizedList(new ArrayList<NioSocketChannel>());
        ChannelInitializer<Channel> init = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                LOGGER.info("Channel: {}, isActive={}, isOpen={}", ch.getClass().getName(), ch.isActive(), ch.isOpen());
                if (ch instanceof NioSocketChannel) {
                    nioChannels.add((NioSocketChannel) ch);
                }
            }
        };
        initializers.put(new NamedSocketAddress("test", new InetSocketAddress(0)), init);
        // The port to channel map keys on the port, post bind. This should be unique even if InetAddress is same
        initializers.put(new NamedSocketAddress("test2", new InetSocketAddress(0)), init);
        ClientConnectionsShutdown ccs = new ClientConnectionsShutdown(
                new DefaultChannelGroup(GlobalEventExecutor.INSTANCE),
                GlobalEventExecutor.INSTANCE,
                /* discoveryClient= */ null);
        EventLoopGroupMetrics elgm = new EventLoopGroupMetrics(Spectator.globalRegistry());
        EventLoopConfig elc = new EventLoopConfig() {
            @Override
            public int eventLoopCount() {
                return 1;
            }

            @Override
            public int acceptorCount() {
                return 1;
            }

            @Override
            public int getBacklogSize() {
                return 1024;
            }
        };
        Server s = new Server(new NoopRegistry(), ssm, initializers, ccs, elgm, elc);
        s.start();

        List<NamedSocketAddress> addrs = s.getListeningAddresses();
        assertThat(addrs.size()).isEqualTo(2);
        for (NamedSocketAddress address : addrs) {
            assertThat(address.unwrap() instanceof InetSocketAddress).isTrue();
            int port = ((InetSocketAddress) address.unwrap()).getPort();
            assertThat(port).isNotEqualTo(0);
            checkConnection(port);
        }

        await().atMost(1, TimeUnit.SECONDS).until(() -> nioChannels.size() == 2);

        nioChannels.stream()
                .map(NioSocketChannel::parent)
                .map(ServerSocketChannel::config)
                .forEach(config -> assertThat(config.getBacklog()).isEqualTo(elc.getBacklogSize()));

        s.stop();

        assertThat(nioChannels.size()).isEqualTo(2);

        for (NioSocketChannel ch : nioChannels) {
            assertThat(ch.isShutdown()).as("isShutdown").isTrue();
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static void checkConnection(int port) {
        Socket sock = null;
        try {
            InetSocketAddress socketAddress = new InetSocketAddress("127.0.0.1", port);
            sock = new Socket();
            sock.setSoTimeout(100);
            sock.connect(socketAddress, 100);
            OutputStream out = sock.getOutputStream();
            out.write("Hello".getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        } catch (Exception exception) {
            fail("checkConnection failed. port=" + port + " " + exception);
        } finally {
            try {
                sock.close();
            } catch (Exception ignored) {
            }
        }
    }
}
