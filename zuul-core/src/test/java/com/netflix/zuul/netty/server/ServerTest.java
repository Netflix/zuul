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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.Spectator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Server}.
 */
@RunWith(JUnit4.class)
public class ServerTest {

    @Test
    public void getListeningSockets() throws Exception {
        ServerStatusManager ssm = mock(ServerStatusManager.class);
        Map<SocketAddress, ChannelInitializer<?>> initializers = new HashMap<>();
        ChannelInitializer<Channel> init = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {}
        };
        initializers.put(new InetSocketAddress(0), init);
        // Pick an InetAddress likely different than the above.  The port to channel map has a unique Key; this
        // prevents the key being a duplicate.
        initializers.put(new InetSocketAddress(InetAddress.getLocalHost(), 0), init);
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
        Server s = new Server(ssm, initializers, ccs, elgm, elc);
        s.start(/* sync= */ false);

        List<SocketAddress> addrs = s.getListeningAddresses();
        assertEquals(2, addrs.size());
        assertTrue(addrs.get(0) instanceof InetSocketAddress);
        assertNotEquals(((InetSocketAddress) addrs.get(0)).getPort(), 0);
        assertTrue(addrs.get(1) instanceof InetSocketAddress);
        assertNotEquals(((InetSocketAddress) addrs.get(1)).getPort(), 0);

        s.stop();
    }
}
