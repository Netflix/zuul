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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class MaxInboundConnectionsHandlerTest {

    @Test
    public void verifyPassportStateAndAttrs() {
        Registry registry = Mockito.mock(Registry.class);
        Counter counter = Mockito.mock(Counter.class);

        when(registry.counter("server.connections.throttled", "id", "test-conn-throttled")).thenReturn(counter);

        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new MaxInboundConnectionsHandler(registry, "test-conn-throttled", 1));

        // Fire twice to increment current conns. count
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();

        verify(counter, times(1)).increment();
        assertEquals(PassportState.SERVER_CH_THROTTLING, CurrentPassport.fromChannel(channel).getState());
        assertTrue(channel.attr(ATTR_CH_THROTTLED).get());


    }
}
