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

package com.netflix.zuul.netty.insights;

import static org.junit.Assert.assertEquals;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.insights.ServerStateHandler.InboundHandler;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServerStateHandlerTest {

    private Registry registry;
    private Id currentConnsId;
    private Id connectsId;
    private Id errorsId;
    private Id closesId;
    private Id exceptionsId;

    final String listener = "test-conn-throttled";

    @Before
    public void init() {
        registry = new DefaultRegistry();
        currentConnsId = registry.createId("server.connections.current").withTags("id", listener);
        connectsId = registry.createId("server.connections.connect").withTags("id", listener);
        closesId = registry.createId("server.connections.close").withTags("id", listener);
        errorsId = registry.createId("server.connections.errors").withTags("id", listener);
        exceptionsId = registry.createId("server.connection.exception").withTags("id", listener);
    }

    @Test
    public void verifyConnMetrics() {

        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(registry, listener));

        final Counter connects = (Counter) registry.get(connectsId);
        final Gauge currentConns = (Gauge) registry.get(currentConnsId);
        final Counter closes = (Counter) registry.get(closesId);
        final Counter errors = (Counter) registry.get(errorsId);

        // Connects X 3
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();

        assertEquals(3.0, currentConns.value(), 0.0);
        assertEquals(3, connects.count());

        // Closes X 1
        channel.pipeline().context(DummyChannelHandler.class).fireChannelInactive();

        assertEquals(2.0, currentConns.value(), 0.0);
        assertEquals(3, connects.count());
        assertEquals(1, closes.count());
        assertEquals(0, errors.count());
    }

    @Test
    public void setPassportStateOnConnect() {

        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(registry, listener));

        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();

        assertEquals(PassportState.SERVER_CH_ACTIVE, CurrentPassport.fromChannel(channel).getState());
    }

    @Test
    public void setPassportStateOnDisconnect() {
        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(registry, listener));

        channel.pipeline().context(DummyChannelHandler.class).fireChannelInactive();

        assertEquals(PassportState.SERVER_CH_INACTIVE, CurrentPassport.fromChannel(channel).getState());
    }

}
