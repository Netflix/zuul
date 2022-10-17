/*
 * Copyright 2022 Netflix, Inc.
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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import com.netflix.zuul.integration.server.Bootstrap;
import com.netflix.zuul.integration.server.HeaderNames;
import io.netty.util.ResourceLeakDetector;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.truth.Truth.assertThat;
import static com.netflix.netty.common.metrics.CustomLeakDetector.assertZeroLeaks;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTest {

    static {
        System.setProperty("io.netty.customResourceLeakDetector",
                CustomLeakDetector.class.getCanonicalName());
    }

    static private Bootstrap bootstrap;
    static private final int ZUUL_SERVER_PORT = findAvailableTcpPort();

    static private final Duration ORIGIN_READ_TIMEOUT = Duration.ofMillis(1000);
    private final String zuulBaseUri = "http://localhost:" + ZUUL_SERVER_PORT;
    private String path;
    private String requestUrl;
    private WireMockRuntimeInfo wmRuntimeInfo;

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .configureStaticDsl(true)
            .options(wireMockConfig().dynamicPort())
            .build();


    @BeforeAll
    static void beforeAll() {
        assertTrue(ResourceLeakDetector.isEnabled());
        assertEquals(ResourceLeakDetector.Level.PARANOID, ResourceLeakDetector.getLevel());

        final int wireMockPort = wireMockExtension.getPort();
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("zuul.server.port.main", ZUUL_SERVER_PORT);
        config.setProperty("api.ribbon.listOfServers", "127.0.0.1:" + wireMockPort);
        config.setProperty("api.ribbon." + CommonClientConfigKey.ReadTimeout.key(), ORIGIN_READ_TIMEOUT.toMillis());
        bootstrap = new Bootstrap();
        bootstrap.start();
        assertTrue(bootstrap.isRunning());
        assertZeroLeaks();
    }

    @AfterAll
    static void afterAll() {
        if (bootstrap != null) {
            bootstrap.stop();
        }
        assertZeroLeaks();
    }


    @BeforeEach
    void beforeEachTest(final WireMockRuntimeInfo wmRuntimeInfo) {
        path = randomPath();
        requestUrl = zuulBaseUri + path;
        this.wmRuntimeInfo = wmRuntimeInfo;
    }

    private static OkHttpClient setupOkHttpClient(final Protocol... protocols) {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.MILLISECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .protocols(Arrays.asList(protocols))
                .build();
    }

    static Stream<Arguments> arguments() {
        return Stream.of(
                    Arguments.of("HTTP 1.1", setupOkHttpClient(Protocol.HTTP_1_1)),
                    Arguments.of("HTTP 2", setupOkHttpClient(Protocol.HTTP_2, Protocol.HTTP_1_1)));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetHappyPath(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();

        wireMock.register(
            get(path)
            .willReturn(
                ok()
                .withBody("hello world")));

        Request request = new Request.Builder().url(zuulBaseUri + path).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("hello world");
        verifyResponseHeaders(response);

        verify(1, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpPostHappyPath(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            post(path)
            .willReturn(
                ok()
                .withBody("Thank you next")));

        Request request = new Request.Builder()
                .url(zuulBaseUri + path)
                .post(RequestBody.create("Simple POST request body".getBytes(StandardCharsets.UTF_8)))
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Thank you next");
        verifyResponseHeaders(response);

        verify(1, postRequestedFor(urlEqualTo(path))
                .withRequestBody(equalTo("Simple POST request body")));
        verify(0, getRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetFailsDueToOriginReadTimeout(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(path)
                .willReturn(
                    ok()
                    .withFixedDelay((int) ORIGIN_READ_TIMEOUT.toMillis() + 50)
                    .withBody("Slow poke")));

        Request request = new Request.Builder().url(zuulBaseUri + path).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(504);
        assertThat(response.body().string()).isEqualTo("");
        verifyResponseHeaders(response);

        verify(2, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetFailsDueToMalformedResponseChunk(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(path)
            .willReturn(
                aResponse()
                .withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        Request request = new Request.Builder().url(zuulBaseUri + path).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThrowsExactly(EOFException.class, () -> {
            response.body().string();
        });

        verify(1, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void zuulWillRetryHttpGetWhenOriginReturns500(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
                get(path)
                        .willReturn(
                                aResponse()
                                        .withStatus(500)));

        Request request = new Request.Builder().url(zuulBaseUri + path).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.body().string()).isEqualTo("");

        verify(2, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void zuulWillRetryHttpGetWhenOriginReturns503(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
                get(path)
                        .willReturn(
                                aResponse()
                                        .withStatus(503)));

        Request request = new Request.Builder().url(zuulBaseUri + path).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(503);
        assertThat(response.body().string()).isEqualTo("");

        verify(2, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetReturnsStatus500DueToConnectionResetByPeer(final String description, final OkHttpClient okHttp) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(path)
            .willReturn(
                    aResponse()
                    .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Request request = new Request.Builder().url(zuulBaseUri + path).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.body().string()).isEqualTo("");

        verify(1, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    private static String randomPath() {
        return "/" + UUID.randomUUID();
    }

    private static int findAvailableTcpPort() {
        try (ServerSocket sock = new ServerSocket(0)) {
            return sock.getLocalPort();
        } catch (IOException e) {
            return -1;
        }
    }

    static private void verifyResponseHeaders(final Response response) {
        assertThat(response.header(HeaderNames.REQUEST_ID)).startsWith("RQ-");
    }

}
