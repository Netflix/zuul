/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.zuul.netty.server.push;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PushMessageSenderInitializer}.
 */
class PushMessageSenderInitializerTest {
    private PushMessageSenderInitializer initializer;
    private Channel channel;
    private ChannelHandler handler;

    @BeforeEach
    void setUp() {
        handler = mock(ChannelHandler.class); // Initialize mock handler

        initializer = new PushMessageSenderInitializer() {
            @Override
            protected void addPushMessageHandlers(ChannelPipeline pipeline) {
                pipeline.addLast("mockHandler", handler);
            }
        };

        channel = new EmbeddedChannel();
    }

    @Test
    void testInitChannel() throws Exception {
        initializer.initChannel(channel);

        assertNotNull(channel.pipeline().context(HttpServerCodec.class));
        assertNotNull(channel.pipeline().context(HttpObjectAggregator.class));
        assertNotNull(channel.pipeline().get("mockHandler"));
    }
}
