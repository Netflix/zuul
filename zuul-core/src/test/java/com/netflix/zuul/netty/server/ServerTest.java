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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Spectator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.DefaultChannelGroup;
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

import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link Server}.
 */
@RunWith(JUnit4.class)
public class ServerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTest.class);

    @Before
    public void beforeTest() {
        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("zuul.server.netty.socket.force_nio", "true");
        config.setProperty("zuul.server.netty.socket.force_io_uring", "false");
    }

    @Test
    public void getListeningSockets() throws Exception {
        ServerStatusManager ssm = mock(ServerStatusManager.class);
        Map<NamedSocketAddress, ChannelInitializer<?>> initializers = new HashMap<>();
        final List<NioSocketChannel> nioChannels = Collections.synchronizedList(new ArrayList<NioSocketChannel>());
        ChannelInitializer<Channel> init = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                LOGGER.info("Channel: "
                        + ch.getClass().getName()
                        + ", isActive="
                        + ch.isActive()
                        + ", isOpen="
                        + ch.isOpen());
                if (ch instanceof NioSocketChannel) {
                    nioChannels.add((NioSocketChannel)ch);
                }
            }
        };
        initializers.put(new NamedSocketAddress("test", new InetSocketAddress(0)), init);
        // The port to channel map keys on the port, post bind. This should be unique even if InetAddress is same
        initializers.put(new NamedSocketAddress("test2", new InetSocketAddress( 0)), init);
        ClientConnectionsShutdown ccs =
                new ClientConnectionsShutdown(
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
        };
        Server s = new Server(new NoopRegistry(), ssm, initializers, ccs, elgm, elc);
        s.start();

        List<NamedSocketAddress> addrs = s.getListeningAddresses();
        assertEquals(2, addrs.size());
        for (NamedSocketAddress address: addrs) {
            assertTrue(address.unwrap() instanceof InetSocketAddress);
            final int port = ((InetSocketAddress) address.unwrap()).getPort();
            assertNotEquals(port, 0);
            checkConnection(port);
        }

        await()
                .atMost(1, SECONDS)
                .until(() -> nioChannels.size() == 2);

        s.stop();

        assertEquals(2, nioChannels.size());

        for (NioSocketChannel ch : nioChannels) {
            assertTrue("isShutdown", ch.isShutdown());
        }
    }

    private static void checkConnection(final int port) {
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
            }
            catch (Exception ignored) { }
        }
    }
}
