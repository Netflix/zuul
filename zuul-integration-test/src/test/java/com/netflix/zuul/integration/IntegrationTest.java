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

import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.google.common.collect.ImmutableSet;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import com.netflix.zuul.integration.server.Bootstrap;
import com.netflix.zuul.integration.server.HeaderNames;
import com.netflix.zuul.integration.server.TestUtil;
import io.netty.handler.codec.compression.Brotli;
import io.netty.util.ResourceLeakDetector;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.truth.Truth.assertThat;
import static com.netflix.netty.common.metrics.CustomLeakDetector.assertZeroLeaks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationTest {

    static {
        System.setProperty("io.netty.customResourceLeakDetector",
            CustomLeakDetector.class.getCanonicalName());
    }


    static private Bootstrap bootstrap;
    static private final int ZUUL_SERVER_PORT = findAvailableTcpPort();

    static private final Duration CLIENT_READ_TIMEOUT = Duration.ofMillis(3000);
    static private final Duration ORIGIN_READ_TIMEOUT = Duration.ofMillis(1000);
    private final String zuulBaseUri = "http://localhost:" + ZUUL_SERVER_PORT;
    private String pathSegment;
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
        this.pathSegment = randomPathSegment();
        this.wmRuntimeInfo = wmRuntimeInfo;
    }

    private static OkHttpClient setupOkHttpClient(final Protocol... protocols) {
        return new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.MILLISECONDS)
            .readTimeout(CLIENT_READ_TIMEOUT)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .protocols(Arrays.asList(protocols))
            .build();
    }

    private Request.Builder setupRequestBuilder(final boolean requestBodyBuffering, final boolean responseBodyBuffering) {
        final HttpUrl url = new HttpUrl.Builder()
            .scheme("http")
            .host("localhost")
            .port(ZUUL_SERVER_PORT)
            .addPathSegment(pathSegment)
            .addQueryParameter("bufferRequestBody", "" + requestBodyBuffering)
            .addQueryParameter("bufferResponseBody", "" + responseBodyBuffering)
            .build();
        return new Request.Builder().url(url);
    }

    static Stream<Arguments> arguments() {
        List<Arguments> list = new ArrayList<Arguments>();
        for (Protocol protocol : ImmutableSet.of(Protocol.HTTP_1_1)) {
            for (boolean requestBodyBuffering : ImmutableSet.of(Boolean.TRUE, Boolean.FALSE)) {
                for (boolean responseBodyBuffering : ImmutableSet.of(Boolean.TRUE, Boolean.FALSE)) {
                    list.add(Arguments.of(
                        protocol.name(),
                        setupOkHttpClient(protocol),
                        requestBodyBuffering,
                        responseBodyBuffering));
                }
            }
        }
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetHappyPath(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();

        wireMock.register(
            get(anyUrl())
                .willReturn(
                    ok()
                        .withBody("hello world")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("hello world");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpPostHappyPath(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            post(anyUrl())
                .willReturn(
                    ok()
                        .withBody("Thank you next")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
            .post(RequestBody.create("Simple POST request body".getBytes(StandardCharsets.UTF_8)))
            .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Thank you next");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpPostWithInvalidHostHeader(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            post(anyUrl())
                .willReturn(
                    ok()
                        .withBody("Thank you next")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
            .addHeader("Host", "_invalid_hostname_")
            .post(RequestBody.create("Simple POST request body".getBytes(StandardCharsets.UTF_8)))
            .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);

        verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetFailsDueToOriginReadTimeout(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    ok()
                        .withFixedDelay((int) ORIGIN_READ_TIMEOUT.toMillis() + 50)
                        .withBody("Slow poke")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(504);
        assertThat(response.body().string()).isEqualTo("");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetFailsDueToMalformedResponseChunk(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        final int expectedStatusCode = (responseBodyBuffering) ? 504 : 200;
        assertThat(response.code()).isEqualTo(expectedStatusCode);
        response.close();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void zuulWillRetryHttpGetWhenOriginReturns500(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withStatus(500)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.body().string()).isEqualTo("");
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void zuulWillRetryHttpGetWhenOriginReturns503(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withStatus(503)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(503);
        assertThat(response.body().string()).isEqualTo("");
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetReturnsStatus500DueToConnectionResetByPeer(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.body().string()).isEqualTo("");
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGet_ServerChunkedDribbleDelay(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("Hello world, is anybody listening?")
                        .withChunkedDribbleDelay(10, (int) CLIENT_READ_TIMEOUT.toMillis() + 500)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get().build();
        Response response = okHttp.newCall(request).execute();
        final int expectedStatusCode = (responseBodyBuffering) ? 504 : 200;
        assertThat(response.code()).isEqualTo(expectedStatusCode);
        response.close();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void blockRequestWithMultipleHostHeaders(final String description, final OkHttpClient okHttp, final boolean requestBodyBuffering, final boolean responseBodyBuffering) throws Exception {
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
                get(anyUrl())
                        .willReturn(aResponse().withStatus(200)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering).get()
                .addHeader("Host", "aaa.example.com")
                .addHeader("Host", "aaa.foobar.com")
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        verify(0, anyRequestedFor(anyUrl()));
        response.close();
    }


    @Test
    void deflateOnly() throws Exception {
        final String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(expectedResponseBody)
                        .withHeader("Content-Type", TestUtil.COMPRESSIBLE_CONTENT_TYPE)));
        URL url = new URL(zuulBaseUri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setAllowUserInteraction(false);
        connection.setRequestProperty("Accept-Encoding", "deflate");
        InputStream inputStream = connection.getInputStream();
        assertEquals(200, connection.getResponseCode());
        assertEquals("text/plain", connection.getHeaderField("Content-Type"));
        assertEquals("deflate", connection.getHeaderField("Content-Encoding"));
        byte[] compressedData = IOUtils.toByteArray(inputStream);
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        byte[] result = new byte[1000];
        int nBytes = inflater.inflate(result);
        String text = new String(result, 0, nBytes, TestUtil.CHARSET);
        assertEquals(expectedResponseBody, text);
        inputStream.close();
        connection.disconnect();
    }

    @Test
    void gzipOnly() throws Exception {
        final String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(expectedResponseBody)
                        .withHeader("Content-Type", TestUtil.COMPRESSIBLE_CONTENT_TYPE)));

        URL url = new URL(zuulBaseUri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setAllowUserInteraction(false);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        InputStream inputStream = connection.getInputStream();
        assertEquals(200, connection.getResponseCode());
        assertEquals("text/plain", connection.getHeaderField("Content-Type"));
        assertEquals("gzip", connection.getHeaderField("Content-Encoding"));
        GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
        byte[] data = IOUtils.toByteArray(gzipInputStream);
        String text = new String(data, TestUtil.CHARSET);
        assertEquals(expectedResponseBody, text);
        inputStream.close();
        gzipInputStream.close();
        connection.disconnect();
    }

    @Test
    void brotliOnly() throws Throwable {
        Brotli.ensureAvailability();
        final String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
                get(anyUrl())
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(expectedResponseBody)
                                        .withHeader("Content-Type", TestUtil.COMPRESSIBLE_CONTENT_TYPE)));

        URL url = new URL(zuulBaseUri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setAllowUserInteraction(false);
        connection.setRequestProperty("Accept-Encoding", "br");
        InputStream inputStream = connection.getInputStream();
        assertEquals(200, connection.getResponseCode());
        assertEquals("text/plain", connection.getHeaderField("Content-Type"));
        assertEquals("br", connection.getHeaderField("Content-Encoding"));
        byte[] compressedData = IOUtils.toByteArray(inputStream);
        assertTrue(compressedData.length > 0);
        DirectDecompress decompressResult = DirectDecompress.decompress(compressedData);
        assertEquals(DecoderJNI.Status.DONE, decompressResult.getResultStatus());
        assertEquals("Hello Hello Hello Hello Hello",
                new String(decompressResult.getDecompressedData(), TestUtil.CHARSET));

        inputStream.close();
        connection.disconnect();
    }

    @Test
    void noCompression() throws Exception {
        final String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;
        final WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(
            get(anyUrl())
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(expectedResponseBody)
                        .withHeader("Content-Type", TestUtil.COMPRESSIBLE_CONTENT_TYPE)));

        URL url = new URL(zuulBaseUri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setAllowUserInteraction(false);
        connection.setRequestProperty("Accept-Encoding", ""); // no compression
        InputStream inputStream = connection.getInputStream();
        assertEquals(200, connection.getResponseCode());
        assertEquals("text/plain", connection.getHeaderField("Content-Type"));
        assertNull(connection.getHeaderField("Content-Encoding"));
        byte[] data = IOUtils.toByteArray(inputStream);
        String text = new String(data, TestUtil.CHARSET);
        assertEquals(expectedResponseBody, text);
        inputStream.close();
        connection.disconnect();
    }

    private static String randomPathSegment() {
        return UUID.randomUUID().toString();
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
