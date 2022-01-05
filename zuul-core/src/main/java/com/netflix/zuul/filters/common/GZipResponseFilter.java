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
import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicStringSetProperty;
import com.netflix.zuul.Filter;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.util.Gzipper;
import com.netflix.zuul.util.HttpUtils;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * General-purpose filter for gzipping/ungzipping response bodies if requested/needed.  This should be run as late as
 * possible to ensure final encoded body length is considered
 *
 * <p>You can just subclass this in your project, and use as-is.
 *
 * @author Mike Smith
 */
@Filter(order = 110, type = FilterType.OUTBOUND)
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

    private static final CachedDynamicBooleanProperty ENABLED =
            new CachedDynamicBooleanProperty("zuul.response.gzip.filter.enabled", true);

    @Override
    public boolean shouldFilter(HttpResponseMessage response) {
        if (!ENABLED.get() || !response.hasBody() || response.getContext().isInBrownoutMode()) {
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
        boolean isResponseGzipped = HttpUtils.isGzipped(respHeaders) || HttpUtils.isDeflated(respHeaders);

        // Decide what to do.;
        final boolean shouldGzip = isGzippableContentType(response) && isGzipRequested && !isResponseGzipped && isRightSizeForGzip(response);
        if (shouldGzip) {
            response.getContext().set(CommonContextKeys.GZIPPER, getGzipper());
        }
        return shouldGzip;
    }

    protected Gzipper getGzipper() {
        return new Gzipper();
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
}
