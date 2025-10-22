package com.netflix.zuul.netty.timeouts;

import com.netflix.spectator.api.Counter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class HttpHeadersTimeoutHandlerTest {
    @Mock
    private Counter timeoutCounter;

    @Test
    public void testTimeout() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpHeadersTimeoutHandler.InboundHandler(
                        () -> true,
                        () -> 0,
                        timeoutCounter,
                        null));

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/");

        assertThrows(ClosedChannelException.class, () -> ch.writeInbound(request));
        verify(timeoutCounter).increment();
        assertNull(ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_TIMEOUT_FUTURE).get());
        assertNull(ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_START_TIME).get());
    }

    @Test
    public void testNoTimeout() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpHeadersTimeoutHandler.InboundHandler(
                        () -> true,
                        () -> 10000,
                        timeoutCounter,
                        null));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/");

        ch.writeInbound(request);

        verify(timeoutCounter, never()).increment();
        assertNull(ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_TIMEOUT_FUTURE).get());
        assertNull(ch.attr(HttpHeadersTimeoutHandler.HTTP_HEADERS_READ_START_TIME).get());
    }
}
