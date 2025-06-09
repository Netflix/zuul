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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.common.collect.ImmutableSet;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.metrics.CustomLeakDetector;
import com.netflix.zuul.integration.server.HeaderNames;
import com.netflix.zuul.integration.server.TestUtil;
import io.netty.channel.epoll.Epoll;
import io.netty.handler.codec.compression.Brotli;
import io.netty.util.ResourceLeakDetector;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class BaseIntegrationTest {

    static {
        System.setProperty("io.netty.customResourceLeakDetector", CustomLeakDetector.class.getCanonicalName());
    }

    private static final Duration CLIENT_READ_TIMEOUT = Duration.ofSeconds(3);
    static final Duration ORIGIN_READ_TIMEOUT = Duration.ofSeconds(1);

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .configureStaticDsl(true)
            .options(wireMockConfig()
                    .maxLoggedResponseSize(1000)
                    .dynamicPort()
                    .useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.ALWAYS)
                    .notifier(new Slf4jNotifier(true)))
            .build();

    @BeforeAll
    static void beforeAll() {
        assertTrue(ResourceLeakDetector.isEnabled());
        assertEquals(ResourceLeakDetector.Level.PARANOID, ResourceLeakDetector.getLevel());
        int wireMockPort = wireMockExtension.getPort();
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("api.ribbon.listOfServers", "127.0.0.1:" + wireMockPort);
        CustomLeakDetector.assertZeroLeaks();
    }

    @AfterAll
    static void afterAll() {
        CustomLeakDetector.assertZeroLeaks();
        ConfigurationManager.getConfigInstance().clear();
    }

    private final int zuulServerPort;
    private final String zuulBaseUri;

    private String pathSegment;
    private WireMock wireMock;

    public BaseIntegrationTest(int zuulServerPort) {
        this.zuulServerPort = zuulServerPort;
        zuulBaseUri = "http://localhost:" + this.zuulServerPort;
    }

    @BeforeEach
    void beforeEachTest() {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("server.http.request.headers.read.timeout.enabled", false);
        config.setProperty("server.http.request.headers.read.timeout", 10000);

        this.pathSegment = randomPathSegment();

        this.wireMock = wireMockExtension.getRuntimeInfo().getWireMock();
    }

    private static OkHttpClient setupOkHttpClient(Protocol... protocols) {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.MILLISECONDS)
                .readTimeout(CLIENT_READ_TIMEOUT)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .protocols(Arrays.asList(protocols))
                .build();
    }

    private Request.Builder setupRequestBuilder(boolean requestBodyBuffering, boolean responseBodyBuffering) {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(zuulServerPort)
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
                            protocol.name(), setupOkHttpClient(protocol), requestBodyBuffering, responseBodyBuffering));
                }
            }
        }
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetHappyPath(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl()).willReturn(ok().withBody("hello world")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("hello world");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpPostHappyPath(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(post(anyUrl()).willReturn(ok().withBody("Thank you next")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .post(RequestBody.create("Simple POST request body".getBytes(StandardCharsets.UTF_8)))
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Thank you next");
        verifyResponseHeaders(response);
    }

    @Test
    void httpPostWithSeveralChunks() throws Exception {
        OkHttpClient okHttp = setupOkHttpClient(Protocol.HTTP_1_1);

        wireMock.register(post(anyUrl()).willReturn(ok().withBody("Thank you next")));

        StreamingRequestBody body = new StreamingRequestBody();
        Request request = setupRequestBuilder(true, false).post(body).build();

        SimpleCallback simpleCallback = new SimpleCallback();
        okHttp.newCall(request).enqueue(simpleCallback);

        body.write("chunk1");
        body.write("chunk2");
        body.write("chunk3");
        body.stop();

        Response response = simpleCallback.getResponse();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Thank you next");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpPostWithInvalidHostHeader(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(post(anyUrl()).willReturn(ok().withBody("Thank you next")));

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
    void httpGetFailsDueToOriginReadTimeout(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl())
                .willReturn(ok().withFixedDelay((int) ORIGIN_READ_TIMEOUT.toMillis() + 50)
                        .withBody("Slow poke")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(504);
        assertThat(response.body().string()).isEqualTo("");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetHappyPathWithHeadersReadTimeout(
            final String description,
            final OkHttpClient okHttp,
            final boolean requestBodyBuffering,
            final boolean responseBodyBuffering)
            throws Exception {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("server.http.request.headers.read.timeout.enabled", true);

        wireMock.register(get(anyUrl()).willReturn(ok().withBody("hello world")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("hello world");
        verifyResponseHeaders(response);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpPostHappyPathWithHeadersReadTimeout(
            final String description,
            final OkHttpClient okHttp,
            final boolean requestBodyBuffering,
            final boolean responseBodyBuffering)
            throws Exception {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("server.http.request.headers.read.timeout.enabled", true);

        wireMock.register(post(anyUrl()).willReturn(ok().withBody("Thank you next")));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .post(RequestBody.create("Simple POST request body".getBytes(StandardCharsets.UTF_8)))
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Thank you next");
        verifyResponseHeaders(response);
    }

    @Test
    void httpGetFailsDueToHeadersReadTimeout() throws Exception {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("server.http.request.headers.read.timeout.enabled", true);
        config.setProperty("server.http.request.headers.read.timeout", 100);

        Socket slowClient = new Socket("localhost", zuulServerPort);
        Thread.sleep(500);
        // end of stream reached because zuul closed the connection
        assertThat(slowClient.getInputStream().read()).isEqualTo(-1);
        slowClient.close();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetFailsDueToMalformedResponseChunk(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        int expectedStatusCode = responseBodyBuffering ? 504 : 200;
        assertThat(response.code()).isEqualTo(expectedStatusCode);
        response.close();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void zuulWillRetryHttpGetWhenOriginReturns500(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl()).willReturn(aResponse().withStatus(500)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.body().string()).isEqualTo("");
        verify(2, getRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void zuulWillRetryHttpGetWhenOriginReturns503(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl()).willReturn(aResponse().withStatus(503)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(503);
        assertThat(response.body().string()).isEqualTo("");
        verify(2, getRequestedFor(anyUrl()));
    }

    @Test
    void zuulWillRetryHttpGetWhenOriginReturns503AndRetryIsSuccessful() throws Exception {
        OkHttpClient okHttp = setupOkHttpClient(Protocol.HTTP_1_1);

        stubFor(get(anyUrl())
                .inScenario("getRetry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("failFirst")
                .willReturn(aResponse().withStatus(200)));

        stubFor(get(anyUrl())
                .inScenario("getRetry")
                .whenScenarioStateIs("failFirst")
                .willSetStateTo("allowSecond")
                .willReturn(aResponse().withStatus(503)));

        stubFor(get(anyUrl())
                .inScenario("getRetry")
                .whenScenarioStateIs("allowSecond")
                .willReturn(aResponse().withStatus(200).withBody("yo")));

        // first call just to ensure a pooled connection will be used later
        Request getRequest = setupRequestBuilder(false, false).get().build();
        assertEquals(200, okHttp.newCall(getRequest).execute().code());

        Request request = setupRequestBuilder(false, false).get().build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("yo");
        verify(3, getRequestedFor(anyUrl()));
    }

    @Test
    void zuulWillRetryHttpPostWhenOriginReturns503AndBodyBuffered() throws Exception {
        OkHttpClient okHttp = setupOkHttpClient(Protocol.HTTP_1_1);

        // make sure a pooled connection will be used in ProxyEndpoint
        wireMock.register(get(anyUrl()).willReturn(aResponse().withStatus(200)));
        Request getRequest = setupRequestBuilder(false, false).get().build();
        assertEquals(200, okHttp.newCall(getRequest).execute().code());

        stubFor(post(anyUrl())
                .inScenario("retry200")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("secondPasses")
                .willReturn(aResponse().withStatus(503)));

        stubFor(post(anyUrl())
                .inScenario("retry200")
                .whenScenarioStateIs("secondPasses")
                .willReturn(aResponse().withStatus(200).withBody("yo")));

        StreamingRequestBody body = new StreamingRequestBody();
        Request request = setupRequestBuilder(true, false).post(body).build();

        body.write("yo");
        body.write("yo1");
        body.write("yo2");
        body.write("yo3");
        body.stop();

        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("yo");
        verify(2, postRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGetReturnsStatus500DueToConnectionResetByPeer(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.body().string()).isEqualTo("");
        verify(1, getRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void httpGet_ServerChunkedDribbleDelay(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Hello world, is anybody listening?")
                        .withChunkedDribbleDelay(10, (int) CLIENT_READ_TIMEOUT.toMillis() + 500)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .build();
        Response response = okHttp.newCall(request).execute();
        int expectedStatusCode = responseBodyBuffering ? 504 : 200;
        assertThat(response.code()).isEqualTo(expectedStatusCode);
        response.close();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void blockRequestWithMultipleHostHeaders(
            String description, OkHttpClient okHttp, boolean requestBodyBuffering, boolean responseBodyBuffering)
            throws Exception {

        wireMock.register(get(anyUrl()).willReturn(aResponse().withStatus(200)));

        Request request = setupRequestBuilder(requestBodyBuffering, responseBodyBuffering)
                .get()
                .addHeader("Host", "aaa.example.com")
                .addHeader("Host", "aaa.foobar.com")
                .build();
        Response response = okHttp.newCall(request).execute();
        assertThat(response.code()).isEqualTo(500);
        verify(0, anyRequestedFor(anyUrl()));
        response.close();
    }

    @Test
    @Disabled
    void deflateOnly() throws Exception {
        String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;

        wireMock.register(get(anyUrl())
                .willReturn(aResponse()
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
        String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;

        wireMock.register(get(anyUrl())
                .willReturn(aResponse()
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
        String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;

        wireMock.register(get(anyUrl())
                .willReturn(aResponse()
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
        assertEquals(
                "Hello Hello Hello Hello Hello", new String(decompressResult.getDecompressedData(), TestUtil.CHARSET));

        inputStream.close();
        connection.disconnect();
    }

    @Test
    void noCompression() throws Exception {
        String expectedResponseBody = TestUtil.COMPRESSIBLE_CONTENT;

        wireMock.register(get(anyUrl())
                .willReturn(aResponse()
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

    @Test
    void jumboOriginResponseShouldBeChunked() throws Exception {
        String expectedResponseBody = TestUtil.JUMBO_RESPONSE_BODY;

        wireMock.register(get(anyUrl())
                .willReturn(aResponse()
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
        assertEquals("chunked", connection.getHeaderField("Transfer-Encoding"));
        byte[] data = IOUtils.toByteArray(inputStream);
        String text = new String(data, TestUtil.CHARSET);
        assertEquals(expectedResponseBody, text);
        inputStream.close();
        connection.disconnect();
    }

    @Test
    @EnabledOnOs(value = {OS.LINUX})
    void epollIsAvailableOnLinux() {
        if (Epoll.unavailabilityCause() != null) {
            Epoll.unavailabilityCause().printStackTrace();
        }
        assertThat(Epoll.isAvailable()).isTrue();
    }

    private static String randomPathSegment() {
        return UUID.randomUUID().toString();
    }

    private static void verifyResponseHeaders(Response response) {
        assertThat(response.header(HeaderNames.REQUEST_ID)).startsWith("RQ-");
    }

    private static class SimpleCallback implements Callback {
        private final CompletableFuture<Response> future = new CompletableFuture<>();

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            future.completeExceptionally(e);
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) {
            future.complete(response);
        }

        public Response getResponse() {
            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("did not get response in a reasonable amount of time");
            }
        }
    }

    private static class StreamingRequestBody extends RequestBody {

        private static final String SENTINEL = "sentinel";

        private final BlockingQueue<String> data = new LinkedBlockingQueue<>();

        void write(String text) {
            data.add(text);
        }

        void stop() {
            data.add(SENTINEL);
        }

        @Override
        public MediaType contentType() {
            return MediaType.get("text/plain");
        }

        @Override
        public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
            String next;
            try {
                while (!(next = data.take()).equals(SENTINEL)) {
                    bufferedSink.writeUtf8(next);
                    bufferedSink.flush();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
