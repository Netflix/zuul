package com.netflix.zuul.netty.insights;

import static org.junit.Assert.assertEquals;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.insights.ServerStateHandler.InboundHandler;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServerStateHandlerTest {


    private Registry registry = new DefaultRegistry();

    private Id currentConnsId;
    private Id connectsId;
    private Id errorsId;
    private Id closesId;

    final String listener = "test-conn-throttled";
    @Before
    public void init() {
        currentConnsId = registry.createId("server.connections.current").withTags("id", listener);
        connectsId = registry.createId("server.connections.connect").withTags("id", listener);
        closesId = registry.createId("server.connections.close").withTags("id", listener);
        errorsId = registry.createId("server.connections.errors").withTags("id", listener);

    }
    @Test
    public void verifyConnMetrics() {

        ServerStateHandler.setRegistry(registry);

        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(listener));

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
}
