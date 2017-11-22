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


package com.netflix.zuul.util;

import com.netflix.client.http.HttpResponse;
import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.zuul.message.HeaderName;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

/**
 * User: michaels@netflix.com
 * Date: 6/8/15
 * Time: 11:50 AM
 */
public class ProxyUtils
{
    private static final CachedDynamicBooleanProperty OVERWRITE_XF_HEADERS = new CachedDynamicBooleanProperty("zuul.headers.xforwarded.overwrite", false);

    private static final Set<HeaderName> RESP_HEADERS_TO_STRIP = new HashSet<>();
    static {
        RESP_HEADERS_TO_STRIP.add(HttpHeaderNames.CONNECTION);
        RESP_HEADERS_TO_STRIP.add(HttpHeaderNames.TRANSFER_ENCODING);
        RESP_HEADERS_TO_STRIP.add(HttpHeaderNames.KEEP_ALIVE);
    }

    private static final Set<HeaderName> REQ_HEADERS_TO_STRIP = new HashSet<>();
    static {
        REQ_HEADERS_TO_STRIP.add(HttpHeaderNames.CONTENT_LENGTH);  // Because the httpclient library sets this itself, and doesn't like it if set by us.
        REQ_HEADERS_TO_STRIP.add(HttpHeaderNames.CONNECTION);
        REQ_HEADERS_TO_STRIP.add(HttpHeaderNames.TRANSFER_ENCODING);
        REQ_HEADERS_TO_STRIP.add(HttpHeaderNames.KEEP_ALIVE);
    }

    public static boolean isValidRequestHeader(HeaderName headerName)
    {
        return ! REQ_HEADERS_TO_STRIP.contains(headerName);
    }

    public static boolean isValidResponseHeader(HeaderName headerName)
    {
        return ! RESP_HEADERS_TO_STRIP.contains(headerName);
    }

    public static void addXForwardedHeaders(HttpRequestMessage request)
    {
        // Add standard Proxy request headers.
        Headers headers = request.getHeaders();
        addXForwardedHeader(headers, HttpHeaderNames.X_FORWARDED_HOST, request.getOriginalHost());
        addXForwardedHeader(headers, HttpHeaderNames.X_FORWARDED_PORT, Integer.toString(request.getPort()));
        addXForwardedHeader(headers, HttpHeaderNames.X_FORWARDED_PROTO, request.getScheme());
        addXForwardedHeader(headers, HttpHeaderNames.X_FORWARDED_FOR, request.getClientIp());
    }

    public static void addXForwardedHeader(Headers headers, HeaderName name, String latestValue)
    {
        if (OVERWRITE_XF_HEADERS.get()) {
            headers.set(name, latestValue);
        }
        else {
            // If this proxy header already exists (possibly due to an upstream ELB or reverse proxy
            // setting it) then keep that value.
            String existingValue = headers.getFirst(name);
            if (existingValue == null) {
                // Otherwise set new value.
                if (latestValue != null) {
                    headers.set(name, latestValue);
                }
            }
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        HttpResponse proxyResp;

        @Mock
        HttpRequestMessage request;

        @Test
        public void testIsValidResponseHeader()
        {
            Assert.assertTrue(isValidResponseHeader(HttpHeaderNames.get("test")));
            Assert.assertFalse(isValidResponseHeader(HttpHeaderNames.get("Keep-Alive")));
            Assert.assertFalse(isValidResponseHeader(HttpHeaderNames.get("keep-alive")));
        }
    }
}
