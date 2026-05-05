/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.zuul.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that when an origin sends an early HTTP/1.1 response (e.g. a 302 redirect) before zuul
 * has finished writing the request body, the affected origin connection is closed rather than
 * returned to the pool, so a follow-up request gets a fresh connection and succeeds.
 */
class EarlyOriginResponseConnectionReuseTest {

    static {
        System.setProperty("io.netty.customResourceLeakDetector", CustomLeakDetector.class.getCanonicalName());
    }

    private static final Duration ORIGIN_READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @RegisterExtension
    static ZuulServerExtension zuulExtension = ZuulServerExtension.newBuilder()
            .withEventLoopThreads(1)
            .withOriginReadTimeout(ORIGIN_READ_TIMEOUT)
            .build();

    private static EventLoopGroup originGroup;
    private static Channel originChannel;

    @BeforeAll
    static void beforeAll() throws Exception {
        assertThat(ResourceLeakDetector.isEnabled()).isTrue();
        assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.PARANOID);
        CustomLeakDetector.assertZeroLeaks();

        originGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        ServerBootstrap b = new ServerBootstrap()
                .group(originGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new EarlyResponseOriginHandler());
                    }
                });
        originChannel = b.bind(0).sync().channel();
        int originPort = ((InetSocketAddress) originChannel.localAddress()).getPort();

        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("api.ribbon.listOfServers", "127.0.0.1:" + originPort);
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (originChannel != null) {
            originChannel.close().sync();
        }
        if (originGroup != null) {
            originGroup.shutdownGracefully().sync();
        }
        ConfigurationManager.getConfigInstance().clearProperty("api.ribbon.listOfServers");
        CustomLeakDetector.assertZeroLeaks();
    }

    @AfterEach
    void afterEach() {
        EarlyResponseOriginHandler.reset();
    }

    @Test
    void poisonedConnectionIsNotReusedAfterEarlyOriginResponse() throws Exception {
        int zuulPort = zuulExtension.getServerPort();

        // declare 2000-byte body but only send 100; origin will respond 302 on headers and never
        // consume the rest, leaving the outbound HttpClientCodec encoder mid-body
        String partialPost = "POST /redirect-on-headers HTTP/1.1\r\n"
                + "Host: localhost:" + zuulPort + "\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: 2000\r\n"
                + "Connection: keep-alive\r\n"
                + "\r\n"
                + "X".repeat(100);

        try (Socket clientSocket = new Socket("localhost", zuulPort)) {
            clientSocket.setSoTimeout((int) AWAIT_TIMEOUT.toMillis());
            clientSocket.getOutputStream().write(partialPost.getBytes(StandardCharsets.US_ASCII));
            clientSocket.getOutputStream().flush();

            assertThat(readStatusLine(clientSocket)).contains("302");
        }

        // wait for zuul to actually tear down the poisoned origin connection
        await().atMost(AWAIT_TIMEOUT).until(() -> EarlyResponseOriginHandler.disconnectedCount.get() == 1);

        try (Socket followupSocket = new Socket("localhost", zuulPort)) {
            followupSocket.setSoTimeout((int) AWAIT_TIMEOUT.toMillis());
            String followupRequest = "GET /healthy HTTP/1.1\r\n"
                    + "Host: localhost:" + zuulPort + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            followupSocket.getOutputStream().write(followupRequest.getBytes(StandardCharsets.US_ASCII));
            followupSocket.getOutputStream().flush();

            assertThat(readStatusLine(followupSocket)).contains("200");
        }

        assertThat(EarlyResponseOriginHandler.connectionCount.get())
                .as("a fresh origin connection should have been opened for the follow-up request")
                .isEqualTo(2);
    }

    private static String readStatusLine(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
    }

    /**
     * Origin server: replies 302 on POST without consuming the body, 200 on GET. Tracks how many
     * TCP connections zuul has opened and closed so the test can synchronise on connection state.
     */
    private static class EarlyResponseOriginHandler extends ChannelInboundHandlerAdapter {

        static final AtomicInteger connectionCount = new AtomicInteger();
        static final AtomicInteger disconnectedCount = new AtomicInteger();

        static void reset() {
            connectionCount.set(0);
            disconnectedCount.set(0);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            connectionCount.incrementAndGet();
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            disconnectedCount.incrementAndGet();
            super.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof HttpRequest request) {
                    if (request.method().equals(HttpMethod.POST)) {
                        ctx.writeAndFlush(buildResponse(HttpResponseStatus.FOUND, "keep-alive"));
                    } else if (request.method().equals(HttpMethod.GET)) {
                        ctx.writeAndFlush(buildResponse(HttpResponseStatus.OK, "close"));
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }

        private static DefaultFullHttpResponse buildResponse(HttpResponseStatus status, String connection) {
            DefaultFullHttpResponse response =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            response.headers().set(HttpHeaderNames.CONNECTION, connection);
            return response;
        }
    }
}
