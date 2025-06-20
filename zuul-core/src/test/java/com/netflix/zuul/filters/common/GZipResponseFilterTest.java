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

package com.netflix.zuul.filters.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GZipResponseFilterTest {
    private final SessionContext context = new SessionContext();
    private final Headers originalRequestHeaders = new Headers();

    @Mock
    private HttpRequestMessage request;

    @Mock
    private HttpRequestMessage originalRequest;

    GZipResponseFilter filter;
    HttpResponseMessage response;

    @BeforeEach
    void setup() {
        // when(request.getContext()).thenReturn(context);
        when(originalRequest.getHeaders()).thenReturn(originalRequestHeaders);

        filter = Mockito.spy(new GZipResponseFilter());
        response = new HttpResponseMessageImpl(context, request, 99);
        response.getHeaders().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        when(response.getInboundRequest()).thenReturn(originalRequest);
    }

    @Test
    void prepareResponseBody_NeedsGZipping() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip");

        byte[] originBody = "blah".getBytes(UTF_8);
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        Mockito.when(filter.isRightSizeForGzip(response)).thenReturn(true); // Force GZip for small response
        response.setHasBody(true);
        assertTrue(filter.shouldFilter(response));

        HttpResponseMessage result = filter.apply(response);
        HttpContent hc1 = filter.processContentChunk(
                response, new DefaultHttpContent(Unpooled.wrappedBuffer(originBody)).retain());
        HttpContent hc2 = filter.processContentChunk(response, new DefaultLastHttpContent());
        byte[] body = new byte[hc1.content().readableBytes() + hc2.content().readableBytes()];
        int hc1Len = hc1.content().readableBytes();
        int hc2Len = hc2.content().readableBytes();
        hc1.content().readBytes(body, 0, hc1Len);
        hc2.content().readBytes(body, hc1Len, hc2Len);

        String bodyStr;
        // Check body is a gzipped version of the origin body.
        try (ByteArrayInputStream bais = new ByteArrayInputStream(body);
                GZIPInputStream gzis = new GZIPInputStream(bais);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int b;
            while ((b = gzis.read()) != -1) {
                baos.write(b);
            }
            bodyStr = baos.toString("UTF-8");
        }
        assertEquals("blah", bodyStr);
        assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"));

        // Check Content-Length header has been removed
        assertEquals(0, result.getHeaders().getAll("Content-Length").size());
    }

    @Test
    void prepareResponseBody_NeedsGZipping_gzipDeflate() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip,deflate");

        byte[] originBody = "blah".getBytes(UTF_8);
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        Mockito.when(filter.isRightSizeForGzip(response)).thenReturn(true); // Force GZip for small response
        response.setHasBody(true);
        assertTrue(filter.shouldFilter(response));

        HttpResponseMessage result = filter.apply(response);
        HttpContent hc1 = filter.processContentChunk(
                response, new DefaultHttpContent(Unpooled.wrappedBuffer(originBody)).retain());
        HttpContent hc2 = filter.processContentChunk(response, new DefaultLastHttpContent());
        byte[] body = new byte[hc1.content().readableBytes() + hc2.content().readableBytes()];
        int hc1Len = hc1.content().readableBytes();
        int hc2Len = hc2.content().readableBytes();
        hc1.content().readBytes(body, 0, hc1Len);
        hc2.content().readBytes(body, hc1Len, hc2Len);

        String bodyStr;
        // Check body is a gzipped version of the origin body.
        try (ByteArrayInputStream bais = new ByteArrayInputStream(body);
                GZIPInputStream gzis = new GZIPInputStream(bais);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int b;
            while ((b = gzis.read()) != -1) {
                baos.write(b);
            }
            bodyStr = baos.toString("UTF-8");
        }
        assertEquals("blah", bodyStr);
        assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"));

        // Check Content-Length header has been removed
        assertEquals(0, result.getHeaders().getAll("Content-Length").size());
    }

    @Test
    void prepareResponseBody_alreadyZipped() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip,deflate");

        byte[] originBody = "blah".getBytes(UTF_8);
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        response.getHeaders().set("Content-Type", "application/json");
        response.getHeaders().set("Content-Encoding", "gzip");
        response.setHasBody(true);
        assertFalse(filter.shouldFilter(response));
    }

    @Test
    void prepareResponseBody_alreadyDeflated() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip,deflate");

        byte[] originBody = "blah".getBytes(UTF_8);
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        response.getHeaders().set("Content-Type", "application/json");
        response.getHeaders().set("Content-Encoding", "deflate");
        response.setHasBody(true);
        assertFalse(filter.shouldFilter(response));
    }

    @Test
    void prepareResponseBody_NeedsGZipping_butTooSmall() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip");
        byte[] originBody = "blah".getBytes(UTF_8);
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        response.setHasBody(true);
        assertFalse(filter.shouldFilter(response));
    }

    @Test
    void prepareChunkedEncodedResponseBody_NeedsGZipping() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip");
        response.getHeaders().set("Transfer-Encoding", "chunked");
        response.setHasBody(true);
        assertTrue(filter.shouldFilter(response));
    }
}
