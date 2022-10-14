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
import io.restassured.internal.http.ResponseParseException;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.http.ConnectionClosedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

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
import static com.netflix.zuul.integration.matchers.IsRequestId.isRequestId;
import static io.restassured.RestAssured.expect;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
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

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .configureStaticDsl(true)
            .options(wireMockConfig().dynamicPort())
            .build();


    @BeforeAll
    static void beforeAll() {
        assertTrue(ResourceLeakDetector.isEnabled());
        assertEquals(ResourceLeakDetector.Level.PARANOID, ResourceLeakDetector.getLevel());
        assertZeroLeaks();

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
    void beforeEachTest() {
        path = randomPath();
    }

    @Test
    void httpGetHappyPath(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();

        wireMock.register(
            get(path)
            .willReturn(
                ok()
                .withBody("hello world")));

        givenZuul()
            .get(path)
            .then()
            .assertThat()
            .spec(responseSpec(200, "hello world"));

        verify(1, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void httpPostHappyPath(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            post(path)
            .willReturn(
                ok()
                .withBody("Thank you next")));

        givenZuul()
            .body("Simple POST request body")
            .post(path)
            .then()
            .assertThat()
            .spec(responseSpec(200, "Thank you next"));

        verify(1, postRequestedFor(urlEqualTo(path))
                .withRequestBody(equalTo("Simple POST request body")));
        verify(0, getRequestedFor(anyUrl()));
    }

    @Test
    void httpGetFailsDueToOriginReadTimeout(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(path)
                .willReturn(
                    ok()
                    .withFixedDelay((int) ORIGIN_READ_TIMEOUT.toMillis() + 50)
                    .withBody("Slow poke")));

        givenZuul()
            .get(path)
            .then()
            .assertThat()
            .spec(responseSpec(504, ""));

        verify(2, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void httpGetFailsDueToMalformedResponseChunk(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(path)
            .willReturn(
                aResponse()
                .withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        final ResponseParseException exception = assertThrowsExactly(ResponseParseException.class, () -> {
            givenZuul()
                .get(path);
        });

        assertThat(exception)
                .hasCauseThat().isInstanceOf(ConnectionClosedException.class);
        assertThat(exception.getCause())
                .hasMessageThat().isEqualTo("Premature end of chunk coded message body: closing chunk expected");

        verify(1, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void zuulWillRetryHttpGetWhenOriginReturns500(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
                get(path)
                        .willReturn(
                                aResponse()
                                        .withStatus(500)));

        givenZuul()
                .get(path)
                .then()
                .assertThat()
                .spec(responseSpec(500, ""));

        verify(2, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void zuulWillRetryHttpGetWhenOriginReturns503(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
                get(path)
                        .willReturn(
                                aResponse()
                                        .withStatus(503)));

        givenZuul()
                .get(path)
                .then()
                .assertThat()
                .spec(responseSpec(503, ""));

        verify(2, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void httpGetReturnsStatus500DueToConnectionResetByPeer(final WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(path)
            .willReturn(
                    aResponse()
                    .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        givenZuul()
            .get(path)
            .then()
            .assertThat()
            .spec(responseSpec(500, ""));

        verify(1, getRequestedFor(urlEqualTo(path)));
        verify(0, postRequestedFor(anyUrl()));
    }

    private RequestSpecification givenZuul() {
        return given().baseUri(zuulBaseUri);
    }

    private static ResponseSpecification responseSpec(final int expectedStatusCode, final String expectedResponseBody) {
        return expect()
                .response()
                .header(HeaderNames.REQUEST_ID, isRequestId())
                .statusCode(expectedStatusCode)
                .body(is(expectedResponseBody));
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
}
