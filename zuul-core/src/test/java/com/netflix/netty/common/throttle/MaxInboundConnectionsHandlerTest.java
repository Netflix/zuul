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

package com.netflix.netty.common.throttle;

import static com.netflix.netty.common.throttle.MaxInboundConnectionsHandler.ATTR_CH_THROTTLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MaxInboundConnectionsHandlerTest {

    private Registry registry = new DefaultRegistry();
    private String listener = "test-throttled";
    private Id counterId;

    @Before
    public void setup() {
        counterId = registry.createId("server.connections.throttled").withTags("id", listener);
    }

    @Test
    public void verifyPassportStateAndAttrs() {

        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new MaxInboundConnectionsHandler(registry, listener, 1));

        // Fire twice to increment current conns. count
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();

        final Counter throttledCount = (Counter) registry.get(counterId);

        assertEquals(1, throttledCount.count());
        assertEquals(PassportState.SERVER_CH_THROTTLING, CurrentPassport.fromChannel(channel).getState());
        assertTrue(channel.attr(ATTR_CH_THROTTLED).get());

    }
}
