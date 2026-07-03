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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Netty HTTP/2 (h2 over TLS) client for integration tests. It negotiates h2 via ALPN, trusts any server
 * certificate (fine for the test's self-signed cert), sends GET requests, and captures inbound GOAWAY frames so a test
 * can assert on graceful connection shutdown.
 */
final class Http2TestClient implements AutoCloseable {

    private final int port;
    private final EventLoopGroup group;
    private final Channel connection;
    private final GoAwayCaptor goAwayCaptor = new GoAwayCaptor();

    Http2TestClient(int port) throws Exception {
        this.port = port;
        this.group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        Protocol.ALPN,
                        SelectorFailureBehavior.NO_ADVERTISE,
                        SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build();

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), "localhost", port));
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                        ch.pipeline().addLast(goAwayCaptor);
                    }
                });

        this.connection = bootstrap.connect("localhost", port).sync().channel();

        SslHandler sslHandler = connection.pipeline().get(SslHandler.class);
        sslHandler.handshakeFuture().get(5, TimeUnit.SECONDS);
        assertThat(sslHandler.applicationProtocol())
                .as("ALPN should have negotiated http/2")
                .isEqualTo(ApplicationProtocolNames.HTTP_2);
    }

    /**
     * Sends a GET on a new stream. The returned future completes with the response {@code :status}, or fails if the
     * stream is refused or reset (e.g. because a GOAWAY has already been received).
     */
    CompletableFuture<Integer> get(String path) throws Exception {
        CompletableFuture<Integer> status = new CompletableFuture<>();
        Http2StreamChannel stream =
                new Http2StreamChannelBootstrap(connection).open().sync().getNow();

        stream.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                try {
                    if (msg instanceof Http2HeadersFrame frame && !status.isDone()) {
                        status.complete(
                                Integer.parseInt(frame.headers().status().toString()));
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                status.completeExceptionally(cause);
            }
        });

        Http2Headers headers = new DefaultHttp2Headers()
                .method("GET")
                .scheme("https")
                .authority("localhost:" + port)
                .path(path);
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true)).addListener(future -> {
            if (!future.isSuccess()) {
                status.completeExceptionally(future.cause());
            }
        });
        return status;
    }

    /**
     * Returns the first GOAWAY frame received, blocking until one arrives.
     */
    GoAway awaitGoAway() {
        return goAwayCaptor.awaitNext();
    }

    /**
     * Waits for the graceful-shutdown GOAWAY - the follow-up frame carrying the real last-stream-id. The initial frame
     * advertises {@code 2^31 - 1} to let in-flight streams finish, so any such frame is skipped.
     */
    GoAway awaitGracefulShutdownGoAway() {
        GoAway goAway = goAwayCaptor.awaitNext();
        while (goAway.lastStreamId() >= Integer.MAX_VALUE) {
            goAway = goAwayCaptor.awaitNext();
        }
        return goAway;
    }

    @Override
    public void close() throws InterruptedException {
        connection.close().sync();
        group.shutdownGracefully().sync();
    }

    public record GoAway(long errorCode, long lastStreamId) {}

    private static final class GoAwayCaptor extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<GoAway> received = new LinkedBlockingQueue<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof Http2GoAwayFrame frame) {
                    received.add(new GoAway(frame.errorCode(), frame.lastStreamId()));
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        GoAway awaitNext() {
            try {
                GoAway goAway = received.poll(5, TimeUnit.SECONDS);
                assertThat(goAway).as("did not receive a GOAWAY frame in time").isNotNull();
                return goAway;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while awaiting a GOAWAY frame", e);
            }
        }
    }
}
