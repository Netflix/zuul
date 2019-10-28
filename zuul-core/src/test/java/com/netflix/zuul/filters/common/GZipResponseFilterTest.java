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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.netflix.zuul.filters.BaseFilterTest;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GZipResponseFilterTest extends BaseFilterTest {

    GZipResponseFilter filter;
    HttpResponseMessage response;

    @Before
    public void setup() {
        super.setup();
        filter = Mockito.spy(new GZipResponseFilter());
        response = new HttpResponseMessageImpl(context, request, 99);
        response.getHeaders().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
    }

    @Test
    public void prepareResponseBody_NeedsGZipping() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip");

        byte[] originBody = "blah".getBytes();
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        Mockito.when(filter.isRightSizeForGzip(response)).thenReturn(true); //Force GZip for small response
        response.setHasBody(true);
        assertTrue(filter.shouldFilter(response));

        final HttpResponseMessage result = filter.apply(response);
        final HttpContent hc1 = filter.processContentChunk(response, new DefaultHttpContent(Unpooled.wrappedBuffer(originBody)).retain());
        final HttpContent hc2 = filter.processContentChunk(response, new DefaultLastHttpContent());
        final byte[] body = new byte[hc1.content().readableBytes() + hc2.content().readableBytes()];
        final int hc1Len = hc1.content().readableBytes();
        final int hc2Len = hc2.content().readableBytes();
        hc1.content().readBytes(body, 0, hc1Len);
        hc2.content().readBytes(body, hc1Len, hc2Len);

        // Check body is a gzipped version of the origin body.
        byte[] unzippedBytes = IOUtils.toByteArray(new GZIPInputStream(new ByteArrayInputStream(body)));
        String bodyStr = new String(unzippedBytes, "UTF-8");
        assertEquals("blah", bodyStr);
        assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"));

        // Check Content-Length header has been removed.;
        assertEquals(0, result.getHeaders().get("Content-Length").size());
    }

    @Test
    public void prepareResponseBody_NeedsGZipping_butTooSmall() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip");
        byte[] originBody = "blah".getBytes();
        response.getHeaders().set("Content-Length", Integer.toString(originBody.length));
        response.setHasBody(true);
        assertFalse(filter.shouldFilter(response));
    }

    @Test
    public void prepareChunkedEncodedResponseBody_NeedsGZipping() throws Exception {
        originalRequestHeaders.set("Accept-Encoding", "gzip");
        response.getHeaders().set("Transfer-Encoding", "chunked");
        response.setHasBody(true);
        assertTrue(filter.shouldFilter(response));
    }
}
