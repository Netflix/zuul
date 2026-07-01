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
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.close.CloseReason;
import com.netflix.netty.common.close.ConnectionCloseEvent;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import io.netty.channel.Channel;
import io.netty.util.ResourceLeakDetector;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
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
class Http1ConnectionCloseIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CLIENT_READ_TIMEOUT = Duration.ofSeconds(5);

    static {
        System.setProperty("io.netty.customResourceLeakDetector", CustomLeakDetector.class.getCanonicalName());
        // close the socket immediately once flagged, rather than waiting out the default connCloseDelay
        ConfigurationManager.getConfigInstance().setProperty("server.connection.close.delay", "0");
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
    private OkHttpClient client;

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
        CustomLeakDetector.assertZeroLeaks();
    }

    @BeforeEach
    void beforeEach() {
        wireMock = wireMockExtension.getRuntimeInfo().getWireMock();
        client = newClient();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        gatedResponse.reset();
        if (client != null) {
            client.connectionPool().evictAll();
        }
        // wait for server to have no channels
        zuulExtension.getClientChannels().newCloseFuture().await(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    @Test
    void closeEventIdleConnection() throws Exception {
        wireMock.register(get(anyUrl()).willReturn(ok().withBody("yo")));

        try (Response first = client.newCall(request()).execute()) {
            assertThat(first.code()).isEqualTo(200);
            assertThat(first.body().string()).isEqualTo("yo");
        }

        // the connection is now idle and kept alive; firing the event should close it
        Channel serverChannel = awaitServerChannel();
        fireCloseEvent(serverChannel);

        await("server should close the idle connection after the close event")
                .atMost(AWAIT_TIMEOUT)
                .until(() -> !serverChannel.isActive());
    }

    @Test
    void closeEventActiveRequest() throws Exception {
        wireMock.register(get(anyUrl()).willReturn(ok().withBody("yo")));
        gatedResponse.init();

        ResponseCallback callback = new ResponseCallback();
        client.newCall(request()).enqueue(callback);

        gatedResponse.awaitRequest();
        Channel serverChannel = awaitServerChannel();
        fireCloseEvent(serverChannel);
        gatedResponse.sendResponse();

        try (Response response = callback.awaitResponse()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("yo");
            assertThat(response.header("Connection")).isEqualToIgnoringCase("close");
        }

        await().atMost(AWAIT_TIMEOUT).until(() -> !serverChannel.isActive());
    }

    private void fireCloseEvent(Channel serverChannel) {
        serverChannel
                .eventLoop()
                .execute(() -> serverChannel
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

    private Request request() {
        return new Request.Builder()
                .url("http://localhost:" + zuulExtension.getServerPort() + "/test")
                .build();
    }

    private static OkHttpClient newClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(CLIENT_READ_TIMEOUT)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .protocols(List.of(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool())
                .build();
    }

    private static final class ResponseCallback implements Callback {
        private final CompletableFuture<Response> future = new CompletableFuture<>();

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            future.completeExceptionally(e);
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) {
            future.complete(response);
        }

        Response awaitResponse() {
            try {
                return future.get(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("did not receive a response in time", e);
            }
        }
    }

    /**
     * WireMock response transformer for allowing deterministic interactions on requests/responses
     */
    private static class GatedResponse implements ResponseTransformerV2 {

        private volatile CountDownLatch requestLatch;
        private volatile CountDownLatch responseLatch;

        void init() {
            requestLatch = new CountDownLatch(1);
            responseLatch = new CountDownLatch(1);
        }

        void awaitRequest() throws InterruptedException {
            assertThat(requestLatch.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                    .as("origin did not receive request in a reasonable amount of time")
                    .isTrue();
        }

        void sendResponse() {
            if (responseLatch != null) {
                responseLatch.countDown();
            }
        }

        void reset() {
            sendResponse();
            requestLatch = null;
            responseLatch = null;
        }

        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(
                com.github.tomakehurst.wiremock.http.Response response, ServeEvent serveEvent) {
            CountDownLatch received = requestLatch;
            CountDownLatch released = responseLatch;
            if (received != null) {
                received.countDown();
                try {
                    released.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return response;
        }

        @Override
        public String getName() {
            return "gated-response";
        }
    }
}
