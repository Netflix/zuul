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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaxInboundConnectionsHandlerTest {

    private Registry registry;
    private Id counterId;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        String listener = "test-throttled";
        registry = new DefaultRegistry();
        counterId = registry.createId("server.connections.throttled").withTags("id", listener);
        channel = new EmbeddedChannel(new MaxInboundConnectionsHandler(registry, listener, 1));
    }

    @Test
    void verifyPassportStateAndAttrs() {
        channel.pipeline().addFirst(new DummyChannelHandler());

        // Fire 1 time, since EmbeddedChannel calls channelActive in the constructor
        channel.pipeline().fireChannelActive();

        Counter throttledCount = (Counter) registry.get(counterId);

        assertThat(throttledCount.count()).isEqualTo(1);
        assertThat(CurrentPassport.fromChannel(channel).getState()).isEqualTo(PassportState.SERVER_CH_THROTTLING);
        assertThat(channel.attr(MaxInboundConnectionsHandler.ATTR_CH_THROTTLED).get())
                .isTrue();
    }

    @Test
    void verifyCloseNotOnPipeline() {
        AtomicBoolean seen = new AtomicBoolean(false);
        channel.pipeline().addLast(new ChannelDuplexHandler() {
            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                seen.set(true);
                ctx.close(promise);
            }
        });

        channel.pipeline().fireChannelActive();

        assertThat(channel.isActive()).isFalse();
        assertThat(seen).isFalse();
    }
}
