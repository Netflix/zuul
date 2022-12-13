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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(HttpUtils.isCompressed("gzip"));
    }

    @Test
    void detectsDeflate() {
        assertTrue(HttpUtils.isCompressed("deflate"));
    }

    @Test
    void detectsCompress() {
        assertTrue(HttpUtils.isCompressed("compress"));
    }

    @Test
    void detectsBR() {
        assertTrue(HttpUtils.isCompressed("br"));
    }

    @Test
    void detectsNonGzip() {
        assertFalse(HttpUtils.isCompressed("identity"));
    }

    @Test
    void detectsGzipAmongOtherEncodings() {
        assertTrue(HttpUtils.isCompressed("gzip, deflate"));
    }

    @Test
    void acceptsGzip() {
        Headers headers = new Headers();
        headers.add("Accept-Encoding", "gzip, deflate");
        assertTrue(HttpUtils.acceptsGzip(headers));
    }

    @Test
    void acceptsGzip_only() {
        Headers headers = new Headers();
        headers.add("Accept-Encoding", "deflate");
        assertFalse(HttpUtils.acceptsGzip(headers));
    }

    @Test
    void stripMaliciousHeaderChars() {
        assertEquals("something", HttpUtils.stripMaliciousHeaderChars("some\r\nthing"));
        assertEquals("some thing", HttpUtils.stripMaliciousHeaderChars("some thing"));
        assertEquals("something", HttpUtils.stripMaliciousHeaderChars("\nsome\r\nthing\r"));
        assertEquals("", HttpUtils.stripMaliciousHeaderChars("\r"));
        assertEquals("", HttpUtils.stripMaliciousHeaderChars(""));
        assertNull(HttpUtils.stripMaliciousHeaderChars(null));
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
        HttpRequestMessage request = new HttpRequestMessageImpl(context, "http", "GET", "/path", queryParams, headers, "127.0.0.1", "scheme", 6666, "server-name");
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
