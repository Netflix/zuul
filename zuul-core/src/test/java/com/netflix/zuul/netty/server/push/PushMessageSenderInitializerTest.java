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
