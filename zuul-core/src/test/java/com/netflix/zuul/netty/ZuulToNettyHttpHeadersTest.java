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
package com.netflix.zuul.netty;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class ZuulToNettyHttpHeadersTest {

    @Test
    void rejectsNegativeInitialSize() {
        assertThatThrownBy(() -> new ZuulToNettyHttpHeaders(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialSize must be non-negative");
    }

    @Test
    void preservesOrderDuplicatesAndOriginalNames() {
        AsciiString firstName = AsciiString.cached("X-First");
        ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(1);
        headers.add(firstName, "one");
        headers.add("x-first", "two");
        headers.add("Second", "three");

        assertThat(headers.size()).isEqualTo(3);
        assertThat(headers.get("X-FIRST")).isEqualTo("one");
        assertThat(headers.getAll("x-FiRsT")).containsExactly("one", "two");
        assertThat(headers.contains("X-FIRST")).isTrue();
        assertThat(headers.entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(tuple("X-First", "one"), tuple("x-first", "two"), tuple("Second", "three"));

        Iterator<Map.Entry<CharSequence, CharSequence>> iterator = headers.iteratorCharSequence();
        Map.Entry<CharSequence, CharSequence> first = iterator.next();
        assertThat(first.getKey()).hasToString("X-First");
        assertThat(first.getValue()).hasToString("one");
    }

    @Test
    void exposesNamesInFirstSeenOrder() {
        ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(3);
        headers.add("X-First", "one");
        headers.add("x-first", "two");
        headers.add("Second", "three");

        Set<String> names = headers.names();

        assertThat(names).containsExactly("X-First", "x-first", "Second");
    }

    @Test
    void supportsMutationAndValueIteration() {
        ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(0);
        headers.add("first", Arrays.asList("one", "two", null, "ignored"));
        headers.add("last", "three");

        assertThat(this.iteratorValues(headers.valueCharSequenceIterator("FIRST")))
                .containsExactly("one", "two");
        assertThat(this.iteratorValues(headers.valueStringIterator("first"))).containsExactly("one", "two");
        assertThat(headers.contains("first", "TWO", true)).isTrue();

        headers.set("FIRST", List.of("replacement-a", "replacement-b"));

        assertThat(headers.entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple("last", "three"), tuple("FIRST", "replacement-a"), tuple("FIRST", "replacement-b"));

        headers.remove("Last");
        assertThat(headers.entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(tuple("FIRST", "replacement-a"), tuple("FIRST", "replacement-b"));

        headers.clear();
        assertThat(headers).isEmpty();
        assertThat(headers.names()).isEmpty();
    }

    @Test
    void preservesExistingValueWhenSetConversionFails() {
        ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(1);
        headers.add("first", "one");

        assertThatThrownBy(() -> headers.set("first", (Object) null)).isInstanceOf(NullPointerException.class);

        assertThat(headers.get("first")).isEqualTo("one");
    }

    @Test
    void convertsNumericAndDateValuesLikeNettyHeaders() {
        Date date = Date.from(Instant.parse("2026-09-18T18:00:00Z"));
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(4);

        headers.addInt("integer", 123);
        headers.setShort("short", (short) 12);
        headers.add("date", date);
        headers.add("calendar", calendar);

        assertThat(headers.getInt("INTEGER")).isEqualTo(123);
        assertThat(headers.getInt("missing", 456)).isEqualTo(456);
        assertThat(headers.getShort("SHORT")).isEqualTo((short) 12);
        assertThat(headers.getShort("missing", (short) 34)).isEqualTo((short) 34);
        assertThat(headers.getTimeMillis("DATE")).isEqualTo(date.getTime());
        assertThat(headers.getTimeMillis("calendar")).isEqualTo(date.getTime());
        assertThat(headers.getTimeMillis("missing", 789)).isEqualTo(789);
    }

    @Test
    void encodesIdenticalH1RequestBytes() {
        HttpHeaders defaultHeaders = this.newDefaultHeaders();
        HttpHeaders boundaryHeaders = new ZuulToNettyHttpHeaders(8);
        this.addCodecHeaders(defaultHeaders);
        this.addCodecHeaders(boundaryHeaders);

        assertThat(this.encodeH1Request(boundaryHeaders)).isEqualTo(this.encodeH1Request(defaultHeaders));
        assertThat(new String(this.encodeH1Request(boundaryHeaders), US_ASCII)).contains("cookie: a=1; b=2\r\n");
    }

    @Test
    void encodesIdenticalH1ResponseBytes() {
        HttpHeaders defaultHeaders = this.newDefaultHeaders();
        HttpHeaders boundaryHeaders = new ZuulToNettyHttpHeaders(4);
        this.addResponseCodecHeaders(defaultHeaders);
        this.addResponseCodecHeaders(boundaryHeaders);

        assertThat(this.encodeH1Response(boundaryHeaders)).isEqualTo(this.encodeH1Response(defaultHeaders));
        assertThat(new String(this.encodeH1Response(boundaryHeaders), US_ASCII))
                .contains("Set-Cookie: a=1\r\nSet-Cookie: b=2\r\n");
    }

    @Test
    void convertsIdenticalH2RequestHeaders() {
        HttpHeaders defaultHeaders = this.newDefaultHeaders();
        HttpHeaders boundaryHeaders = new ZuulToNettyHttpHeaders(8);
        this.addCodecHeaders(defaultHeaders);
        this.addCodecHeaders(boundaryHeaders);

        Http2Headers defaultH2 = this.convertRequestToH2(defaultHeaders);
        Http2Headers boundaryH2 = this.convertRequestToH2(boundaryHeaders);

        assertThat(this.headerPairs(boundaryH2)).containsExactlyElementsOf(this.headerPairs(defaultH2));
        assertThat(boundaryH2.getAll("cookie"))
                .extracting(CharSequence::toString)
                .containsExactly("a=1", "b=2", "c=3");
        assertThat(boundaryH2.contains("connection")).isFalse();
        assertThat(boundaryH2.contains("x-remove")).isFalse();
        assertThat(boundaryH2.get("x-keep")).hasToString("value");
    }

    @Test
    void convertsIdenticalH2ResponseHeaders() {
        HttpHeaders defaultHeaders = this.newDefaultHeaders();
        HttpHeaders boundaryHeaders = new ZuulToNettyHttpHeaders(4);
        this.addResponseCodecHeaders(defaultHeaders);
        this.addResponseCodecHeaders(boundaryHeaders);

        Http2Headers defaultH2 = this.convertResponseToH2(defaultHeaders);
        Http2Headers boundaryH2 = this.convertResponseToH2(boundaryHeaders);

        assertThat(this.headerPairs(boundaryH2)).containsExactlyElementsOf(this.headerPairs(defaultH2));
        assertThat(boundaryH2.getAll("set-cookie"))
                .extracting(CharSequence::toString)
                .containsExactly("a=1", "b=2");
        assertThat(boundaryH2.contains("connection")).isFalse();
        assertThat(boundaryH2.get("x-keep")).hasToString("value");
    }

    private HttpHeaders newDefaultHeaders() {
        return DefaultHttpHeadersFactory.headersFactory().withValidation(false).newHeaders();
    }

    private void addCodecHeaders(HttpHeaders headers) {
        headers.add("host", "origin.test");
        headers.add("x-http2-scheme", "https");
        headers.add("cookie", "a=1; b=2");
        headers.add("Cookie", "c=3");
        headers.add("connection", "keep-alive, x-remove");
        headers.add("x-remove", "filtered");
        headers.add("x-keep", "value");
        headers.add("te", "gzip, trailers");
    }

    private void addResponseCodecHeaders(HttpHeaders headers) {
        headers.add("Set-Cookie", "a=1");
        headers.add("Set-Cookie", "b=2");
        headers.add("connection", "keep-alive");
        headers.add("x-keep", "value");
    }

    private byte[] encodeH1Request(HttpHeaders headers) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/path?query=value",
                Unpooled.EMPTY_BUFFER,
                headers,
                EmptyHttpHeaders.INSTANCE);
        return this.encodeH1(channel, request);
    }

    private byte[] encodeH1Response(HttpHeaders headers) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER, headers, EmptyHttpHeaders.INSTANCE);
        return this.encodeH1(channel, response);
    }

    private byte[] encodeH1(EmbeddedChannel channel, HttpMessage message) {
        try {
            assertThat(channel.writeOutbound(message)).isTrue();
            ByteBuf encoded = channel.readOutbound();

            try {
                return ByteBufUtil.getBytes(encoded);
            } finally {
                ReferenceCountUtil.release(encoded);
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private Http2Headers convertRequestToH2(HttpHeaders headers) {
        HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/path?query=value", headers);
        return HttpConversionUtil.toHttp2Headers(request, false);
    }

    private Http2Headers convertResponseToH2(HttpHeaders headers) {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers);
        return HttpConversionUtil.toHttp2Headers(response, false);
    }

    private List<String> headerPairs(Http2Headers headers) {
        List<String> pairs = new ArrayList<>(headers.size());

        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            pairs.add(entry.getKey() + "\u0000" + entry.getValue());
        }

        return pairs;
    }

    private List<String> iteratorValues(Iterator<? extends CharSequence> iterator) {
        List<String> values = new ArrayList<>();

        while (iterator.hasNext()) {
            values.add(iterator.next().toString());
        }

        return values;
    }
}
