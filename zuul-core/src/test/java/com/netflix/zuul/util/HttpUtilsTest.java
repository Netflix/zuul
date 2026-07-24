/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link HttpUtils}.
 */
class HttpUtilsTest {

    @Test
    void detectsGzip() {
        assertThat(HttpUtils.isCompressed("gzip")).isTrue();
    }

    @Test
    void detectsDeflate() {
        assertThat(HttpUtils.isCompressed("deflate")).isTrue();
    }

    @Test
    void detectsCompress() {
        assertThat(HttpUtils.isCompressed("compress")).isTrue();
    }

    @Test
    void detectsBR() {
        assertThat(HttpUtils.isCompressed("br")).isTrue();
    }

    @Test
    void detectsNonGzip() {
        assertThat(HttpUtils.isCompressed("identity")).isFalse();
    }

    @Test
    void detectsGzipAmongOtherEncodings() {
        assertThat(HttpUtils.isCompressed("gzip, deflate")).isTrue();
    }

    @Test
    void acceptsGzip() {
        Headers headers = new Headers();
        headers.add("Accept-Encoding", "gzip, deflate");
        assertThat(HttpUtils.acceptsGzip(headers)).isTrue();
    }

    @Test
    void acceptsGzip_only() {
        Headers headers = new Headers();
        headers.add("Accept-Encoding", "deflate");
        assertThat(HttpUtils.acceptsGzip(headers)).isFalse();
    }

    @Test
    void stripMaliciousHeaderChars() {
        assertThat(HttpUtils.stripMaliciousHeaderChars("some\r\nthing")).isEqualTo("something");
        assertThat(HttpUtils.stripMaliciousHeaderChars("some thing")).isEqualTo("some thing");
        assertThat(HttpUtils.stripMaliciousHeaderChars("\nsome\r\nthing\r")).isEqualTo("something");
        assertThat(HttpUtils.stripMaliciousHeaderChars("\r")).isEqualTo("");
        assertThat(HttpUtils.stripMaliciousHeaderChars("")).isEqualTo("");
        assertThat(HttpUtils.stripMaliciousHeaderChars(null)).isNull();
    }

    @Test
    void getBodySizeIfKnown_returnsContentLengthValue() {
        SessionContext context = new SessionContext();
        Headers headers = new Headers();
        headers.add(com.netflix.zuul.message.http.HttpHeaderNames.CONTENT_LENGTH, "23450");
        ZuulMessage msg = new ZuulMessageImpl(context, headers);
        assertThat(HttpUtils.getBodySizeIfKnown(msg)).isEqualTo(Integer.valueOf(23450));
    }

    @Test
    void getBodySizeIfKnown_returnsResponseBodySize() {
        SessionContext context = new SessionContext();
        Headers headers = new Headers();
        HttpQueryParams queryParams = new HttpQueryParams();
        HttpRequestMessage request = new HttpRequestMessageImpl(
                context, "http", "GET", "/path", queryParams, headers, "127.0.0.1", "scheme", 6666, "server-name");
        request.storeInboundRequest();
        HttpResponseMessage response = new HttpResponseMessageImpl(context, request, 200);
        response.setBodyAsText("Hello world");
        assertThat(HttpUtils.getBodySizeIfKnown(response)).isEqualTo(Integer.valueOf(11));
    }

    @Test
    void getBodySizeIfKnown_returnsNull() {
        SessionContext context = new SessionContext();
        Headers headers = new Headers();
        ZuulMessage msg = new ZuulMessageImpl(context, headers);
        assertThat(HttpUtils.getBodySizeIfKnown(msg)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "/path?a=b, /path",
        "/path?, /path",
        "/path#frag, /path",
        "/path?a=b#frag, /path",
        // traversal hidden in the query is discarded, not normalized into the path
        "/path?a=../../etc, /path",
        // path is still normalized after the query is stripped
        "/../etc?x=1, /etc",
    })
    void parsePath_stripsQueryAndFragment(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    // The query is stripped by hand precisely because java.net.URI rejects characters that are legal
    // in real query strings; a valid path with a "dirty" query must resolve, not 400. Guards the
    // manual strip - without it, parsing the full URI-with-query would throw on these.
    @ParameterizedTest
    @CsvSource({
        "/search?q=a|b|c, /search",
        "/p?x=a b, /p",
        "/p?v=^1.0, /p",
        "/p?j={a:1}, /p",
    })
    void parsePath_stripsQueryWithUriIllegalChars(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/resource, /api/v1/resource",
        "/, /",
        "/a/b/c/, /a/b/c/",
    })
    void parsePath_returnsCleanPathUnchanged(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    // The guard requires origin-form (leading slash), so every other request-target form - relative,
    // absolute-form, asterisk, opaque scheme, empty - is rejected up front. The caller turns the
    // URISyntaxException into a 400 (see ClientRequestReceiver), so nothing non-origin-form is routed.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "../../etc/passwd",
                "..%2f..%2fetc",
                "....//",
                "http://host/../../etc/passwd",
                "http://host/a/../b",
                "mailto:foo@bar.com",
                "tel:12345",
                "javascript:alert(1)",
                "urn:isbn:123",
                "*",
                "",
            })
    void parsePath_rejectsNonOriginFormTargets(String uri) {
        assertThatThrownBy(() -> HttpUtils.parsePath(uri))
                .isInstanceOf(URISyntaxException.class)
                .hasMessageContaining("leading slash");
    }

    @ParameterizedTest
    @CsvSource({
        "/a/b/../c, /a/c",
        "/a/./b, /a/b",
        "/a/b/.., /a/",
        "/a/b/../, /a/",
        "/a/./../b, /b",
        "/./../a, /a",
        "/a//b, /a/b",
    })
    void parsePath_collapsesPlainDotSegments(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "/a/%2e%2e/b, /b",
        "/a/%2E%2E/b, /b",
        "/a/%2e/b, /a/b",
        "/a/.%2e/b, /b",
        "/.%2e/.%2e/etc, /etc",
        "/%2e%2e/%2e%2e/etc, /etc",
    })
    void parsePath_decodesThenCollapsesPercentEncodedDots(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        // a pure "/.." chain clamps to root, not to an empty path
        "/.., /",
        "/../.., /",
        "/../, /",
        "/../etc, /etc",
        "/../../etc, /etc",
        "/../../../../etc/passwd, /etc/passwd",
        "/a/../../etc, /etc",
        "/%2e%2e/etc, /etc",
        // authority-form: the fake host is dropped and the escape neutralized
        "//etc/passwd, /passwd",
        "//host/../../x, /x",
    })
    void parsePath_clampsLeadingEscapesToRoot(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    // %2f stays encoded, so a ".." wrapped in encoded slashes is a single opaque segment that
    // normalize cannot see through. This is the defense boundary: downstream code that later
    // decodes %2f must not treat the result as an already-normalized path.
    @ParameterizedTest
    @CsvSource({
        "/a%2f..%2f..%2fb, /a%2f..%2f..%2fb",
        "/..%2f..%2fetc, /..%2f..%2fetc",
        "/%2e%2e%2f%2e%2e%2fetc, /..%2f..%2fetc",
        "/a/%2e%2e%2f%2e%2e/etc, /a/..%2f../etc",
        // uppercase %2F is left encoded too - only %2e/%2E are decoded
        "/a%2F..%2F..%2Fb, /a%2F..%2F..%2Fb",
    })
    void parsePath_doesNotDecodeEncodedSlashes(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    // None of these are a "." or ".." path segment, so there is nothing for normalize to collapse -
    // they pass through verbatim rather than being mistaken for traversal.
    @ParameterizedTest
    @CsvSource({
        "/%252e%252e/etc, /%252e%252e/etc",
        "/..%00, /..%00",
        "/a/..;/b, /a/..;/b",
        "/..., /...",
        "/...., /....",
        "/foo/....//bar, /foo/..../bar",
        // interior traversal still collapses even with an adjacent encoded null byte
        "/a%00/../b, /b",
    })
    void parsePath_leavesNonDotDotSequencesUntouched(String uri, String expected) {
        assertThat(parsePath(uri)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"//", "/\\..\\..\\etc"})
    void parsePath_rejectsMalformedUris(String uri) {
        assertThatThrownBy(() -> HttpUtils.parsePath(uri)).isInstanceOf(URISyntaxException.class);
    }

    @Test
    void parsePath_throwsNpeOnNull() {
        assertThatThrownBy(() -> HttpUtils.parsePath(null)).isInstanceOf(NullPointerException.class);
    }

    private static String parsePath(String uri) {
        try {
            return HttpUtils.parsePath(uri);
        } catch (URISyntaxException e) {
            throw new AssertionError("did not expect parsePath to reject [" + uri + "]", e);
        }
    }
}
