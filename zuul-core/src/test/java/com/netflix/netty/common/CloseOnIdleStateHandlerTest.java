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

package com.netflix.netty.common;

import static io.netty.handler.timeout.IdleStateEvent.ALL_IDLE_STATE_EVENT;
import static org.junit.Assert.assertEquals;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CloseOnIdleStateHandlerTest {

    private Registry registry = new DefaultRegistry();
    private Id counterId;
    private final String listener = "test-idle-state";

    @Before
    public void setup() {
        counterId = registry.createId("server.connections.idle.timeout").withTags("id", listener);
    }

    @Test
    public void incrementCounterOnIdleStateEvent() {
        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new CloseOnIdleStateHandler(registry, listener));

        channel.pipeline().context(DummyChannelHandler.class).fireUserEventTriggered(ALL_IDLE_STATE_EVENT);

        final Counter idleTimeouts = (Counter) registry.get(counterId);
        assertEquals(1, idleTimeouts.count());
    }
}
