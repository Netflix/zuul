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

import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.netty.connectionpool.ClientChannelManager;
import com.netflix.zuul.netty.connectionpool.ConnectionPoolConfig;
import com.netflix.zuul.netty.connectionpool.DefaultClientChannelManager;
import com.netflix.zuul.netty.connectionpool.DefaultOriginChannelInitializer;
import com.netflix.zuul.netty.connectionpool.OriginChannelInitializer;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import com.netflix.zuul.origins.BasicNettyOrigin;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.origins.OriginName;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Verifies that TLS consumption of a late terminal body chunk cannot empty the copy retained for a retry. */
class LateLastContentRetryIntegrationTest {

    static {
        System.setProperty("io.netty.customResourceLeakDetector", CustomLeakDetector.class.getCanonicalName());
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final byte[] REQUEST_BODY = repeatedBytes('A', 1024);
    private static final CompletableFuture<Void> FIRST_ORIGIN_HEADERS = new CompletableFuture<>();
    private static final CompletableFuture<RecordedRequest> FIRST_ORIGIN_REQUEST = new CompletableFuture<>();
    private static final CompletableFuture<RecordedRequest> RETRY_ORIGIN_REQUEST = new CompletableFuture<>();
    private static final CompletableFuture<HttpResponseStatus> CLIENT_RESPONSE = new CompletableFuture<>();
    private static final SslContext CLIENT_SSL_CONTEXT = buildClientSslContext();

    @RegisterExtension
    static ZuulServerExtension zuulExtension = ZuulServerExtension.newBuilder()
            .withEventLoopThreads(1)
            .withOriginReadTimeout(TIMEOUT)
            .withOriginManagerFactory(LateLastContentRetryIntegrationTest::newOriginManager)
            .build();

    private static EventLoopGroup eventLoopGroup;
    private static Channel firstOrigin;
    private static Channel retryOrigin;
    private static SelfSignedCertificate certificate;

    @BeforeAll
    static void beforeAll() throws Exception {
        assertThat(ResourceLeakDetector.isEnabled()).isTrue();
        assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.PARANOID);
        CustomLeakDetector.assertZeroLeaks();

        certificate = new SelfSignedCertificate("localhost");
        SslContext serverSslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
                .sslProvider(SslProvider.JDK)
                .build();
        eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        firstOrigin = startOrigin(serverSslContext, true, FIRST_ORIGIN_REQUEST);
        retryOrigin = startOrigin(serverSslContext, false, RETRY_ORIGIN_REQUEST);

        int firstOriginPort = ((InetSocketAddress) firstOrigin.localAddress()).getPort();
        int retryOriginPort = ((InetSocketAddress) retryOrigin.localAddress()).getPort();
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("api.ribbon.IsSecure", true);
        config.setProperty("api.ribbon.MaxAutoRetriesNextServer", 1);
        config.setProperty("api.ribbon.NFLoadBalancerRuleClassName", "com.netflix.loadbalancer.RoundRobinRule");
        // RoundRobinRule increments before selecting, so index 1 is the first attempt.
        config.setProperty(
                "api.ribbon.listOfServers", "127.0.0.1:" + retryOriginPort + ",127.0.0.1:" + firstOriginPort);
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (firstOrigin != null) {
            firstOrigin.close().sync();
        }
        if (retryOrigin != null) {
            retryOrigin.close().sync();
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully().sync();
        }
        if (certificate != null) {
            certificate.delete();
        }
        ConfigurationManager.getConfigInstance().clear();
        CustomLeakDetector.assertZeroLeaks();
    }

    @Test
    @Timeout(15)
    void tlsRetryPreservesLateLastContentBody() throws Exception {
        Channel client = startClient();
        try {
            DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/late-last");
            request.headers().set(HttpHeaderNames.HOST, "localhost:" + zuulExtension.getServerPort());
            request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, REQUEST_BODY.length);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            client.writeAndFlush(request).sync();

            FIRST_ORIGIN_HEADERS.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            client.writeAndFlush(new DefaultLastHttpContent(Unpooled.wrappedBuffer(REQUEST_BODY)))
                    .sync();

            RecordedRequest first = FIRST_ORIGIN_REQUEST.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            RecordedRequest retry = RETRY_ORIGIN_REQUEST.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(first.target()).isEqualTo("POST /late-last");
            assertThat(first.contentMessages()).isEqualTo(1);
            assertThat(first.firstContentWasLast()).isTrue();
            assertThat(first.body()).containsExactly(REQUEST_BODY);
            assertThat(retry.target()).isEqualTo(first.target());
            assertThat(retry.body()).containsExactly(first.body());
            assertThat(CLIENT_RESPONSE.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                    .isEqualTo(HttpResponseStatus.OK);
        } finally {
            client.close().sync();
        }
    }

    private static OriginManager<?> newOriginManager(Registry registry) {
        return new BasicNettyOriginManager(registry) {
            @Override
            public BasicNettyOrigin createOrigin(OriginName originName, String uri, SessionContext context) {
                return new BasicNettyOrigin(originName, registry) {
                    @Override
                    protected ClientChannelManager createClientChannelManager(
                            OriginName name, IClientConfig clientConfig, Registry spectatorRegistry) {
                        return new TestClientChannelManager(name, clientConfig, spectatorRegistry);
                    }
                };
            }
        };
    }

    private static final class TestClientChannelManager extends DefaultClientChannelManager {

        private TestClientChannelManager(OriginName originName, IClientConfig clientConfig, Registry registry) {
            super(originName, clientConfig, registry);
        }

        @Override
        protected OriginChannelInitializer createChannelInitializer(
                IClientConfig clientConfig, ConnectionPoolConfig connectionPoolConfig, Registry registry) {
            return new DefaultOriginChannelInitializer(connectionPoolConfig, registry) {
                @Override
                protected SslContext getClientSslContext(Registry spectatorRegistry) {
                    return CLIENT_SSL_CONTEXT;
                }

                @Override
                protected void initChannel(Channel channel) throws Exception {
                    super.initChannel(channel);
                    // NIO wraps ECONNRESET in SocketException; native transports expose the errno Zuul maps directly.
                    channel.pipeline()
                            .addBefore(
                                    CONNECTION_POOL_HANDLER,
                                    "nioConnectionResetMapper",
                                    new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                                            if (cause instanceof SocketException
                                                    && "Connection reset".equals(cause.getMessage())) {
                                                context.fireExceptionCaught(new OriginConnectException(
                                                        "Origin connection reset", OutboundErrorType.RESET_CONNECTION));
                                            } else {
                                                context.fireExceptionCaught(cause);
                                            }
                                        }
                                    });
                }
            };
        }
    }

    private static Channel startOrigin(
            SslContext sslContext, boolean resetAfterRequest, CompletableFuture<RecordedRequest> request)
            throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_LINGER, resetAfterRequest ? 0 : -1)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(sslContext.newHandler(channel.alloc()));
                        channel.pipeline().addLast(new HttpServerCodec());
                        channel.pipeline().addLast(new RecordingOriginHandler(resetAfterRequest, request));
                    }
                });
        return bootstrap.bind(0).sync().channel();
    }

    private static Channel startClient() throws InterruptedException {
        return new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new HttpClientCodec());
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext context, Object message) {
                                if (message instanceof HttpResponse response) {
                                    CLIENT_RESPONSE.complete(response.status());
                                }
                                ReferenceCountUtil.release(message);
                            }
                        });
                    }
                })
                .connect("localhost", zuulExtension.getServerPort())
                .sync()
                .channel();
    }

    private static final class RecordingOriginHandler extends ChannelInboundHandlerAdapter {

        private final boolean resetAfterRequest;
        private final CompletableFuture<RecordedRequest> requestReceived;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private String target;
        private int contentMessages;
        private boolean firstContentWasLast;

        private RecordingOriginHandler(boolean resetAfterRequest, CompletableFuture<RecordedRequest> requestReceived) {
            this.resetAfterRequest = resetAfterRequest;
            this.requestReceived = requestReceived;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            try {
                if (message instanceof HttpRequest request) {
                    target = request.method() + " " + request.uri();
                    if (resetAfterRequest) {
                        FIRST_ORIGIN_HEADERS.complete(null);
                    }
                }
                if (message instanceof HttpContent content) {
                    if (contentMessages++ == 0) {
                        firstContentWasLast = content instanceof LastHttpContent;
                    }
                    append(body, content.content());
                    if (content instanceof LastHttpContent) {
                        requestReceived.complete(
                                new RecordedRequest(target, body.toByteArray(), contentMessages, firstContentWasLast));
                        if (resetAfterRequest) {
                            // Bypass SslHandler's close_notify so Zuul observes a transport reset.
                            context.channel().unsafe().close(context.channel().voidPromise());
                        } else {
                            writeResponse(context);
                        }
                    }
                }
            } finally {
                ReferenceCountUtil.release(message);
            }
        }
    }

    private static void writeResponse(ChannelHandlerContext context) {
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        context.writeAndFlush(response);
    }

    private static SslContext buildClientSslContext() {
        try {
            return SslContextBuilder.forClient()
                    .sslProvider(SslProvider.JDK)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void append(ByteArrayOutputStream target, ByteBuf source) {
        byte[] bytes = new byte[source.readableBytes()];
        source.getBytes(source.readerIndex(), bytes);
        target.writeBytes(bytes);
    }

    private static byte[] repeatedBytes(char value, int count) {
        byte[] bytes = new byte[count];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private record RecordedRequest(String target, byte[] body, int contentMessages, boolean firstContentWasLast) {}
}
