/*
 * Copyright 2018 Netflix, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicStringSetProperty;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.filters.BaseFilterTest;
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.util.Gzipper;
import com.netflix.zuul.util.HttpUtils;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * General-purpose filter for gzipping/ungzipping response bodies if requested/needed.
 *
 * You can just subclass this in your project, and use as-is.
 *
 * @author Mike Smith
 */
public class GZipResponseFilter extends HttpOutboundSyncFilter
{
    private static DynamicStringSetProperty GZIPPABLE_CONTENT_TYPES = new DynamicStringSetProperty("zuul.gzip.contenttypes",
            "text/html,application/x-javascript,text/css,application/javascript,text/javascript,text/plain,text/xml," +
                    "application/json,application/vnd.ms-fontobject,application/x-font-opentype,application/x-font-truetype," +
                    "application/x-font-ttf,application/xml,font/eot,font/opentype,font/otf,image/svg+xml,image/vnd.microsoft.icon",
            ",");

    // https://webmasters.stackexchange.com/questions/31750/what-is-recommended-minimum-object-size-for-gzip-performance-benefits
    private static final CachedDynamicIntProperty MIN_BODY_SIZE_FOR_GZIP =
            new CachedDynamicIntProperty("zuul.min.gzip.body.size", 860);

    @Override
    public int filterOrder() {
        return 5;
    }

    @Override
    public boolean shouldFilter(HttpResponseMessage response) {
        if (!response.hasBody() || response.getContext().isInBrownoutMode()) {
            return false;
        }

        if (response.getContext().get(CommonContextKeys.GZIPPER) != null) {
            return true;
        }

        // A flag on SessionContext can be set to override normal mechanism of checking if client accepts gzip.;
        final HttpRequestInfo request = response.getInboundRequest();
        final Boolean overrideIsGzipRequested = (Boolean) response.getContext().get(CommonContextKeys.OVERRIDE_GZIP_REQUESTED);
        final boolean isGzipRequested = (overrideIsGzipRequested == null) ?
                HttpUtils.acceptsGzip(request.getHeaders()) :  overrideIsGzipRequested.booleanValue();

        // Check the headers to see if response is already gzipped.
        final Headers respHeaders = response.getHeaders();
        boolean isResponseGzipped = HttpUtils.isGzipped(respHeaders);

        // Decide what to do.;
        final boolean shouldGzip = isGzippableContentType(response) && isGzipRequested && !isResponseGzipped && isRightSizeForGzip(response);
        if (shouldGzip) {
            response.getContext().set(CommonContextKeys.GZIPPER, new Gzipper());
        }
        return shouldGzip;
    }

    @VisibleForTesting
    boolean isRightSizeForGzip(HttpResponseMessage response) {
        final Integer bodySize = HttpUtils.getBodySizeIfKnown(response);
        //bodySize == null is chunked encoding which is eligible for gzip compression
        return (bodySize == null) || (bodySize.intValue() >= MIN_BODY_SIZE_FOR_GZIP.get());
    }

    @Override
    public HttpResponseMessage apply(HttpResponseMessage response) {
        // set Gzip headers
        final Headers respHeaders = response.getHeaders();
        respHeaders.set(HttpHeaderNames.CONTENT_ENCODING, "gzip");
        respHeaders.remove(HttpHeaderNames.CONTENT_LENGTH);
        return response;
    }

    private boolean isGzippableContentType(HttpResponseMessage response) {
        String ct = response.getHeaders().getFirst(HttpHeaderNames.CONTENT_TYPE);
        if (ct != null) {
            int charsetIndex = ct.indexOf(';');
            if (charsetIndex > 0) {
                ct = ct.substring(0, charsetIndex);
            }
            return GZIPPABLE_CONTENT_TYPES.get().contains(ct.toLowerCase());
        }
        return false;
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage resp, HttpContent chunk) {
        final Gzipper gzipper = (Gzipper) resp.getContext().get(CommonContextKeys.GZIPPER);
        gzipper.write(chunk);
        if (chunk instanceof LastHttpContent) {
            gzipper.finish();
            return new DefaultLastHttpContent(gzipper.getByteBuf());
        } else {
            return new DefaultHttpContent(gzipper.getByteBuf());
        }
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {
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

}
