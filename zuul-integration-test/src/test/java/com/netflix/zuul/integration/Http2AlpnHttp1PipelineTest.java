/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.zuul.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.netty.common.close.Http1ConnectionCloseHandler;
import com.netflix.netty.common.close.Http1ConnectionExpiryHandler;
import com.netflix.zuul.netty.server.Http1DecoderFailureRejectingHandler;
import com.netflix.zuul.netty.server.Http1FramingEnforcingHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that a connection which negotiates HTTP/1.1 through ALPN on the HTTP/2 TLS listener is given the
 * same HTTP/1.1 handler set as the plaintext listener.
 *
 * <p>{@link com.netflix.zuul.netty.server.http2.Http2SslChannelInitializer} builds its ALPN HTTP/1.1
 * fallback pipeline by hand rather than by calling {@code addHttp1Handlers}, so it is possible for that
 * pipeline to drift from the one every other listener gets. This test pins the two together.
 */
class Http2AlpnHttp1PipelineTest {

    @RegisterExtension
    static ZuulServerExtension zuulExtension = ZuulServerExtension.newBuilder()
            .withEventLoopThreads(1)
            .withOriginReadTimeout(Duration.ofSeconds(5))
            .build();

    private static SSLSocket connectWithAlpnHttp1(int port) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());

        SSLSocket socket = (SSLSocket) ctx.getSocketFactory().createSocket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
        SSLParameters params = socket.getSSLParameters();
        params.setApplicationProtocols(new String[] {"http/1.1"});
        socket.setSSLParameters(params);
        socket.startHandshake();
        return socket;
    }

    @Test
    void alpnHttp1FallbackGetsTheSameHttp1HandlersAsThePlaintextListener() throws Exception {
        try (SSLSocket socket = connectWithAlpnHttp1(zuulExtension.getHttp2Port())) {
            assertThat(socket.getApplicationProtocol())
                    .as("the listener must negotiate HTTP/1.1 for this test to mean anything")
                    .isEqualTo("http/1.1");

            // Send a request so that the ALPN handler swaps in the HTTP/1.1 pipeline.
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write("GET /alpn-pipeline-probe HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            assertThat(in.read()).as("the server must answer before the pipeline is inspected").isNotNegative();

            Channel serverChannel = zuulExtension.getClientChannels().stream()
                    .filter(Channel::isActive)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no active server-side channel was registered"));

            ChannelPipeline pipeline = serverChannel.pipeline();
            List<Class<? extends ChannelHandler>> handlers = StreamSupport.stream(
                            pipeline.spliterator(), false)
                    .map(entry -> entry.getValue().getClass())
                    .collect(Collectors.toList());

            assertThat(handlers)
                    .as("the ALPN HTTP/1.1 pipeline must reject requests whose framing the codec could not decode")
                    .contains(Http1DecoderFailureRejectingHandler.class);
            assertThat(handlers)
                    .as("the ALPN HTTP/1.1 pipeline must enforce RFC 9112 section 6.3 request framing")
                    .contains(Http1FramingEnforcingHandler.class);
            assertThat(handlers)
                    .as("the ALPN HTTP/1.1 pipeline must honour connection close")
                    .contains(Http1ConnectionCloseHandler.class);
            assertThat(handlers)
                    .as("the ALPN HTTP/1.1 pipeline must honour connection expiry")
                    .contains(Http1ConnectionExpiryHandler.class);
        }
    }
}
