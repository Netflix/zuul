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

import com.netflix.zuul.message.Headers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
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
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures header copies at the Netty-Zuul boundaries with representative synthetic profiles.
 * Run with: ./gradlew -p oss --no-daemon :zuul-core:jmh
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HeaderHandoffBenchmark {

    /**
     * Copies Netty headers through the iterator used by ClientRequestReceiver today.
     */
    @Benchmark
    public Headers nettyToZuulStringIterator(InboundState state) {
        Headers headers = new Headers(state.nettyHeaders.size());
        Iterator<Map.Entry<String, String>> iterator = state.nettyHeaders.iteratorAsString();

        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            headers.add(entry.getKey(), entry.getValue());
        }

        return headers;
    }

    /**
     * Copies Netty headers without allocating Netty's per-entry String wrapper.
     */
    @Benchmark
    public Headers nettyToZuulCharSequenceIterator(InboundState state) {
        Headers headers = new Headers(state.nettyHeaders.size());
        Iterator<Map.Entry<CharSequence, CharSequence>> iterator = state.nettyHeaders.iteratorCharSequence();

        while (iterator.hasNext()) {
            Map.Entry<CharSequence, CharSequence> entry = iterator.next();
            headers.add(entry.getKey().toString(), entry.getValue().toString());
        }

        return headers;
    }

    /**
     * Copies Zuul headers into the DefaultHttpHeaders representation used today.
     */
    @Benchmark
    public HttpHeaders zuulToDefaultStringNames(OutboundState state) {
        return state.newDefaultStringHeaders();
    }

    /**
     * Copies Zuul headers into DefaultHttpHeaders using shared AsciiString names.
     */
    @Benchmark
    public HttpHeaders zuulToDefaultSharedAsciiNames(OutboundState state) {
        return state.newDefaultSharedAsciiHeaders();
    }

    /**
     * Copies Zuul headers into the representation used at the Netty boundary.
     */
    @Benchmark
    public HttpHeaders zuulToNettyHeaders(OutboundState state) {
        return state.newZuulToNettyHeaders();
    }

    /**
     * Creates a read-only view over prepared name-value pairs as a zero-copy lower bound.
     */
    @Benchmark
    public HttpHeaders directReadOnlyView(OutboundState state) {
        return state.newReadOnlyHeaders();
    }

    /**
     * Copies current String headers and encodes the resulting H1 message.
     */
    @Benchmark
    public int h1DefaultStringNames(OutboundState state) {
        return state.encodeH1(state.newDefaultStringHeaders());
    }

    /**
     * Copies shared AsciiString names and encodes the resulting H1 message.
     */
    @Benchmark
    public int h1DefaultSharedAsciiNames(OutboundState state) {
        return state.encodeH1(state.newDefaultSharedAsciiHeaders());
    }

    /**
     * Copies headers into the Netty boundary representation and encodes an H1 message.
     */
    @Benchmark
    public int h1ZuulToNettyHeaders(OutboundState state) {
        return state.encodeH1(state.newZuulToNettyHeaders());
    }

    /**
     * Encodes prepared name-value pairs through ReadOnlyHttpHeaders as the H1 lower bound.
     */
    @Benchmark
    public int h1ReadOnlyView(OutboundState state) {
        return state.encodeH1(state.newReadOnlyHeaders());
    }

    /**
     * Copies current String headers and converts the resulting message to H2 headers.
     */
    @Benchmark
    public Http2Headers h2DefaultStringNames(OutboundState state) {
        return state.convertToH2(state.newDefaultStringHeaders());
    }

    /**
     * Copies shared AsciiString names and converts the resulting message to H2 headers.
     */
    @Benchmark
    public Http2Headers h2DefaultSharedAsciiNames(OutboundState state) {
        return state.convertToH2(state.newDefaultSharedAsciiHeaders());
    }

    /**
     * Copies headers into the Netty boundary representation and converts it to H2 headers.
     */
    @Benchmark
    public Http2Headers h2ZuulToNettyHeaders(OutboundState state) {
        return state.convertToH2(state.newZuulToNettyHeaders());
    }

    /**
     * Converts prepared name-value pairs through ReadOnlyHttpHeaders as the H2 lower bound.
     */
    @Benchmark
    public Http2Headers h2ReadOnlyView(OutboundState state) {
        return state.convertToH2(state.newReadOnlyHeaders());
    }

    /**
     * Holds the Netty source headers for inbound handoff measurements.
     */
    @State(Scope.Thread)
    public static class InboundState {
        @Param({"REQUEST_SMALL", "REQUEST_MEDIUM", "RESPONSE_SMALL", "RESPONSE_LARGE"})
        public Profile profile;

        private HttpHeaders nettyHeaders;

        /**
         * Builds decoded H1-style source headers once per benchmark trial.
         */
        @Setup(Level.Trial)
        public void setUp() {
            this.nettyHeaders = DefaultHttpHeadersFactory.headersFactory()
                    .withValidation(false)
                    .newHeaders();

            for (HeaderEntry entry : this.profile.entries()) {
                this.nettyHeaders.add(AsciiString.cached(entry.name()), entry.value());
            }
        }
    }

    /**
     * Holds Zuul source headers and H1 codec state for outbound handoff measurements.
     */
    @State(Scope.Thread)
    public static class OutboundState {
        @Param({"REQUEST_LARGE", "REQUEST_XLARGE", "RESPONSE_SMALL", "RESPONSE_LARGE"})
        public Profile profile;

        private Headers zuulHeaders;
        private Map<String, AsciiString> sharedNames;
        private CharSequence[] readOnlyPairs;
        private EmbeddedChannel h1Channel;

        /**
         * Builds the synthetic Zuul headers and reusable lower-bound storage.
         */
        @Setup(Level.Trial)
        public void setUp() {
            List<HeaderEntry> entries = this.profile.entries();
            this.zuulHeaders = new Headers(entries.size());
            Map<String, AsciiString> names = new LinkedHashMap<>();
            this.readOnlyPairs = new CharSequence[entries.size() * 2];

            for (int i = 0; i < entries.size(); i++) {
                HeaderEntry entry = entries.get(i);
                AsciiString sharedName = names.computeIfAbsent(entry.name(), AsciiString::cached);
                this.zuulHeaders.add(entry.name(), entry.value());
                this.readOnlyPairs[i * 2] = sharedName;
                this.readOnlyPairs[i * 2 + 1] = entry.value();
            }

            this.sharedNames = Collections.unmodifiableMap(names);
            this.verifyZuulToNettySemantics(entries);

            ChannelHandler encoder =
                    switch (this.profile.messageType()) {
                        case REQUEST -> new HttpRequestEncoder();
                        case RESPONSE -> new HttpResponseEncoder();
                    };
            this.h1Channel = new EmbeddedChannel(encoder);
        }

        /**
         * Releases any codec output left after a failed benchmark invocation.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            this.h1Channel.finishAndReleaseAll();
        }

        private HttpHeaders newDefaultStringHeaders() {
            HttpHeaders headers = this.newDefaultHeaders();
            this.zuulHeaders.forEach((name, value) -> headers.add(name, value));
            return headers;
        }

        private HttpHeaders newDefaultSharedAsciiHeaders() {
            HttpHeaders headers = this.newDefaultHeaders();
            this.zuulHeaders.forEach((name, value) -> {
                AsciiString sharedName = Objects.requireNonNull(this.sharedNames.get(name));
                headers.add(sharedName, value);
            });
            return headers;
        }

        private HttpHeaders newZuulToNettyHeaders() {
            int additionalCapacity = this.profile.messageType() == MessageType.RESPONSE ? 2 : 0;
            ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(this.zuulHeaders.size() + additionalCapacity);
            this.zuulHeaders.forEach((name, value) -> headers.add(name, value));
            return headers;
        }

        private HttpHeaders newReadOnlyHeaders() {
            return new ReadOnlyHttpHeaders(false, this.readOnlyPairs);
        }

        private HttpHeaders newDefaultHeaders() {
            return DefaultHttpHeadersFactory.headersFactory()
                    .withValidation(false)
                    .newHeaders();
        }

        private int encodeH1(HttpHeaders headers) {
            Object message =
                    switch (this.profile.messageType()) {
                        case REQUEST ->
                            new DefaultFullHttpRequest(
                                    HttpVersion.HTTP_1_1,
                                    HttpMethod.GET,
                                    "/",
                                    Unpooled.EMPTY_BUFFER,
                                    headers,
                                    EmptyHttpHeaders.INSTANCE);
                        case RESPONSE ->
                            new DefaultFullHttpResponse(
                                    HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.OK,
                                    Unpooled.EMPTY_BUFFER,
                                    headers,
                                    EmptyHttpHeaders.INSTANCE);
                    };

            if (!this.h1Channel.writeOutbound(message)) {
                throw new IllegalStateException("H1 encoder produced no output for " + this.profile);
            }

            int encodedBytes = 0;
            Object output;

            while ((output = this.h1Channel.readOutbound()) != null) {
                if (output instanceof ByteBuf buffer) {
                    encodedBytes += buffer.readableBytes();
                }
                ReferenceCountUtil.release(output);
            }

            return encodedBytes;
        }

        private Http2Headers convertToH2(HttpHeaders headers) {
            HttpMessage message =
                    switch (this.profile.messageType()) {
                        case REQUEST -> new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", headers);
                        case RESPONSE -> new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers);
                    };
            return HttpConversionUtil.toHttp2Headers(message, false);
        }

        private void verifyZuulToNettySemantics(List<HeaderEntry> entries) {
            ZuulToNettyHttpHeaders headers = new ZuulToNettyHttpHeaders(entries.size());

            for (HeaderEntry entry : entries) {
                headers.add(entry.name(), entry.value());
            }

            if (headers.size() != entries.size()) {
                throw new IllegalStateException("Zuul-to-Netty header count differs for " + this.profile);
            }

            Iterator<Map.Entry<CharSequence, CharSequence>> iterator = headers.iteratorCharSequence();

            for (HeaderEntry expected : entries) {
                if (!iterator.hasNext()) {
                    throw new IllegalStateException("Zuul-to-Netty iterator ended early for " + this.profile);
                }
                Map.Entry<CharSequence, CharSequence> actual = iterator.next();

                if (!AsciiString.contentEquals(expected.name(), actual.getKey())
                        || !AsciiString.contentEquals(expected.value(), actual.getValue())) {
                    throw new IllegalStateException("Zuul-to-Netty iteration differs for " + this.profile);
                }
            }

            if (iterator.hasNext()) {
                throw new IllegalStateException("Zuul-to-Netty iterator returned extra entries for " + this.profile);
            }

            String duplicateName = this.profile.messageType() == MessageType.REQUEST ? "cookie" : "set-cookie";
            List<String> expectedValues = entries.stream()
                    .filter(entry -> entry.name().equals(duplicateName))
                    .map(HeaderEntry::value)
                    .toList();
            List<String> actualValues = headers.getAll(duplicateName.toUpperCase(Locale.ROOT));

            if (!expectedValues.equals(actualValues) || !headers.contains(duplicateName.toUpperCase(Locale.ROOT))) {
                throw new IllegalStateException("Zuul-to-Netty duplicate lookup differs for " + this.profile);
            }

            ZuulToNettyHttpHeaders mutableHeaders = new ZuulToNettyHttpHeaders(2);
            mutableHeaders.add("Mixed-Case", "first");
            mutableHeaders.add("mixed-case", "second");
            mutableHeaders.set("MIXED-CASE", "replacement");

            if (!List.of("replacement").equals(mutableHeaders.getAll("mixed-case"))) {
                throw new IllegalStateException("Zuul-to-Netty set did not replace duplicate values");
            }

            mutableHeaders.remove("mixed-CASE");

            if (!mutableHeaders.isEmpty()) {
                throw new IllegalStateException("Zuul-to-Netty remove did not use case-insensitive matching");
            }

            mutableHeaders.add("name", "value");
            mutableHeaders.clear();

            if (!mutableHeaders.isEmpty()) {
                throw new IllegalStateException("Zuul-to-Netty clear retained entries");
            }
        }
    }

    /**
     * Representative synthetic header counts and serialized byte sizes.
     */
    public enum Profile {
        REQUEST_SMALL(24, 2_400, MessageType.REQUEST),
        REQUEST_MEDIUM(40, 3_600, MessageType.REQUEST),
        REQUEST_LARGE(80, 16_000, MessageType.REQUEST),
        REQUEST_XLARGE(100, 28_000, MessageType.REQUEST),
        RESPONSE_SMALL(28, 1_800, MessageType.RESPONSE),
        RESPONSE_LARGE(40, 3_000, MessageType.RESPONSE);

        private static final List<String> REQUEST_HEADER_NAMES = List.of(
                "host",
                "x-http2-scheme",
                "user-agent",
                "accept",
                "accept-encoding",
                "accept-language",
                "accept-charset",
                "content-type",
                "authorization",
                "cookie",
                "cookie",
                "origin",
                "referer",
                "cache-control",
                "pragma",
                "x-forwarded-for",
                "x-forwarded-proto",
                "x-forwarded-host",
                "x-forwarded-port",
                "forwarded",
                "traceparent",
                "tracestate",
                "baggage",
                "x-request-id",
                "x-request-metadata");

        private static final List<String> RESPONSE_HEADER_NAMES = List.of(
                "date",
                "content-type",
                "cache-control",
                "etag",
                "expires",
                "last-modified",
                "vary",
                "content-encoding",
                "accept-ranges",
                "age",
                "server",
                "location",
                "retry-after",
                "www-authenticate",
                "content-language",
                "content-location",
                "access-control-allow-origin",
                "access-control-allow-credentials",
                "access-control-expose-headers",
                "timing-allow-origin",
                "set-cookie",
                "set-cookie",
                "traceparent",
                "tracestate",
                "x-request-id",
                "x-cache",
                "x-response-metadata");

        private final int count;
        private final int serializedBytes;
        private final MessageType messageType;

        Profile(int count, int serializedBytes, MessageType messageType) {
            this.count = count;
            this.serializedBytes = serializedBytes;
            this.messageType = messageType;
        }

        private MessageType messageType() {
            return this.messageType;
        }

        private List<HeaderEntry> entries() {
            List<HeaderEntry> entries = new ArrayList<>(this.count);
            List<Integer> expandableEntries = new ArrayList<>(this.count);

            for (int i = 0; i < this.count; i++) {
                String name = this.headerName(i);
                String value = this.baseValue(name, i);
                entries.add(new HeaderEntry(name, value));

                if (!this.hasFixedValue(name)) {
                    expandableEntries.add(i);
                }
            }

            int remainingBytes = this.serializedBytes - this.serializedBytes(entries);

            if (remainingBytes < 0 || expandableEntries.isEmpty()) {
                throw new IllegalStateException("Profile cannot reach target size: " + this);
            }

            int bytesPerEntry = remainingBytes / expandableEntries.size();
            int remainder = remainingBytes % expandableEntries.size();

            for (int i = 0; i < expandableEntries.size(); i++) {
                int entryIndex = expandableEntries.get(i);
                HeaderEntry entry = entries.get(entryIndex);
                int extensionLength = bytesPerEntry + (i < remainder ? 1 : 0);
                char fill = (char) ('a' + entryIndex % 26);
                entries.set(
                        entryIndex,
                        new HeaderEntry(
                                entry.name(),
                                entry.value() + String.valueOf(fill).repeat(extensionLength)));
            }

            this.validate(entries);
            return List.copyOf(entries);
        }

        private String headerName(int index) {
            List<String> names = this.messageType == MessageType.REQUEST
                    ? Profile.REQUEST_HEADER_NAMES
                    : Profile.RESPONSE_HEADER_NAMES;

            if (index < names.size()) {
                return names.get(index);
            }

            String prefix = this.messageType == MessageType.REQUEST
                    ? "x-synthetic-request-header-"
                    : "x-synthetic-response-header-";
            return prefix + index;
        }

        private String baseValue(String name, int index) {
            return switch (name) {
                case "host" -> "origin.test";
                case "x-http2-scheme" -> "https";
                case "cookie" -> "cookie-a-" + index + "=synthetic; cookie-b-" + index + "=synthetic";
                case "set-cookie" -> "cookie-" + index + "=synthetic; Path=/; Secure; HttpOnly";
                case "date", "expires", "last-modified" -> "Fri, 18 Sep 2026 18:00:00 GMT";
                case "age", "retry-after" -> "60";
                case "traceparent" -> "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
                default -> "synthetic-" + index + '-';
            };
        }

        private boolean hasFixedValue(String name) {
            return switch (name) {
                case "host",
                        "x-http2-scheme",
                        "date",
                        "expires",
                        "last-modified",
                        "age",
                        "retry-after",
                        "traceparent" -> true;
                default -> false;
            };
        }

        private int serializedBytes(List<HeaderEntry> entries) {
            int bytes = 0;

            for (HeaderEntry entry : entries) {
                bytes += entry.name().length() + 2 + entry.value().length() + 2;
            }

            return bytes;
        }

        private void validate(List<HeaderEntry> entries) {
            if (entries.size() != this.count || this.serializedBytes(entries) != this.serializedBytes) {
                throw new IllegalStateException("Generated headers differ from profile " + this);
            }

            for (HeaderEntry entry : entries) {
                if (!this.isAscii(entry.name()) || !this.isAscii(entry.value())) {
                    throw new IllegalStateException("Generated non-ASCII header for " + this);
                }
            }
        }

        private boolean isAscii(String value) {
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) > 127) {
                    return false;
                }
            }

            return true;
        }
    }

    private enum MessageType {
        REQUEST,
        RESPONSE
    }

    private record HeaderEntry(String name, String value) {}
}
