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

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import org.junit.jupiter.api.Test;

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
}
