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

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.close.CloseReason;
import com.netflix.netty.common.close.ConnectionCloseEvent;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.util.ResourceLeakDetector;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * @author Justin Guerra
 * @since 7/1/26
 */
class Http2ConnectionCloseIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_REQUESTS_PER_CONNECTION = 2;

    static {
        System.setProperty("io.netty.customResourceLeakDetector", CustomLeakDetector.class.getCanonicalName());
        // close the connection immediately once flagged, rather than waiting out the default connCloseDelay
        ConfigurationManager.getConfigInstance().setProperty("server.connection.close.delay", "0");
        // expire connections after a couple of requests so expiry can be exercised deterministically (the close-event
        // tests only issue a single request per connection, so they never trip this)
        ConfigurationManager.getConfigInstance()
                .setProperty("server.connection.max.requests", String.valueOf(MAX_REQUESTS_PER_CONNECTION));
    }

    private static final GatedResponse gatedResponse = new GatedResponse();

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().extensions(gatedResponse))
            .build();

    @RegisterExtension
    static ZuulServerExtension zuulExtension = ZuulServerExtension.newBuilder()
            .withEventLoopThreads(1)
            .withOriginReadTimeout(Duration.ofSeconds(5))
            .build();

    private WireMock wireMock;

    @BeforeAll
    static void beforeAll() {
        assertThat(ResourceLeakDetector.isEnabled()).isTrue();
        assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.PARANOID);
        CustomLeakDetector.assertZeroLeaks();

        ConfigurationManager.getConfigInstance()
                .setProperty("api.ribbon.listOfServers", "127.0.0.1:" + wireMockExtension.getPort());
    }

    @AfterAll
    static void afterAll() {
        ConfigurationManager.getConfigInstance().clearProperty("api.ribbon.listOfServers");
        ConfigurationManager.getConfigInstance().clearProperty("server.connection.close.delay");
        ConfigurationManager.getConfigInstance().clearProperty("server.connection.max.requests");
        CustomLeakDetector.assertZeroLeaks();
    }

    @BeforeEach
    void beforeEach() {
        wireMock = wireMockExtension.getRuntimeInfo().getWireMock();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        gatedResponse.reset();
        // ensure all server channels close before the next test
        zuulExtension.getClientChannels().newCloseFuture().await(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    @Test
    void closeEventIdleConnection() throws Exception {
        wireMock.register(get(anyUrl()).willReturn(ok().withBody("hello")));

        try (Http2TestClient client = new Http2TestClient(zuulExtension.getHttp2Port())) {
            assertThat(client.get("/test").get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .isEqualTo(200);

            // the connection is now idle; firing the event should send a GOAWAY and close it
            Channel serverChannel = awaitServerChannel();
            fireCloseEvent(serverChannel);

            assertThat(client.awaitGoAway().errorCode()).isEqualTo(Http2Error.NO_ERROR.code());
            await("server should close the http/2 connection after the close event")
                    .atMost(AWAIT_TIMEOUT)
                    .until(() -> !serverChannel.isActive());
        }
    }

    @Test
    void closeEventActiveRequest() throws Exception {
        wireMock.register(get(anyUrl()).willReturn(ok().withBody("hello")));
        gatedResponse.init();

        try (Http2TestClient client = new Http2TestClient(zuulExtension.getHttp2Port())) {
            CompletableFuture<Integer> status = client.get("/test");

            gatedResponse.awaitRequest();
            Channel serverChannel = awaitServerChannel();
            fireCloseEvent(serverChannel);

            assertThat(client.awaitGoAway().errorCode()).isEqualTo(Http2Error.NO_ERROR.code());

            // the in-flight stream is below the GOAWAY's last-stream-id, so it still completes
            gatedResponse.sendResponse();
            assertThat(status.get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(200);

            await().atMost(AWAIT_TIMEOUT).until(() -> !serverChannel.isActive());
        }
    }

    @Test
    void newStreamIsRefusedAfterGoAway() throws Exception {
        wireMock.register(get(anyUrl()).willReturn(ok().withBody("hello")));
        gatedResponse.init();

        try (Http2TestClient client = new Http2TestClient(zuulExtension.getHttp2Port())) {
            // hold a stream in flight so the connection stays open through the graceful shutdown window
            CompletableFuture<Integer> inFlight = client.get("/test");
            gatedResponse.awaitRequest();
            fireCloseEvent(awaitServerChannel());

            // wait for the second GOAWAY, which carries the real last-stream-id (the first advertises 2^31-1)
            Http2TestClient.GoAway goAway = client.awaitGracefulShutdownGoAway();
            assertThat(goAway.errorCode()).isEqualTo(Http2Error.NO_ERROR.code());

            // a stream opened now is above the last-stream-id, so the client should refuse to create it
            CompletableFuture<Integer> refused = client.get("/test");
            assertThatThrownBy(() -> refused.get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .as("a new stream opened after the GOAWAY must be refused")
                    .hasCauseInstanceOf(Http2Exception.class);

            // the already-in-flight stream is below the last-stream-id, so it still completes
            gatedResponse.sendResponse();
            assertThat(inFlight.get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .isEqualTo(200);
        }
    }

    @Test
    void connectionExpiresAfterMaxRequests() throws Exception {
        wireMock.register(get(anyUrl()).willReturn(ok().withBody("hello")));

        try (Http2TestClient client = new Http2TestClient(zuulExtension.getHttp2Port())) {
            // each request is served normally until the one that reaches the limit expires the connection
            for (int i = 0; i < MAX_REQUESTS_PER_CONNECTION; i++) {
                assertThat(client.get("/test").get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                        .isEqualTo(200);
            }

            assertThat(client.awaitGoAway().errorCode()).isEqualTo(Http2Error.NO_ERROR.code());
            await("server should close the connection once it expires")
                    .atMost(AWAIT_TIMEOUT)
                    .until(() -> zuulExtension.getClientChannels().stream().noneMatch(Channel::isActive));
        }
    }

    private void fireCloseEvent(Channel serverChannel) {
        serverChannel.eventLoop().execute(() -> serverChannel
                .pipeline()
                .fireUserEventTriggered(new ConnectionCloseEvent.Graceful(CloseReason.SHUTDOWN)));
    }

    private static Channel awaitServerChannel() {
        await().atMost(AWAIT_TIMEOUT)
                .until(() -> zuulExtension.getClientChannels().stream().anyMatch(Channel::isActive));
        return zuulExtension.getClientChannels().stream()
                .filter(Channel::isActive)
                .findFirst()
                .orElseThrow();
    }
}
