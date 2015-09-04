/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.util;

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestInfo;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 11:05 PM
 */
public class HttpUtils
{
    /**
     * Get the IP address of client making the request.
     *
     * Uses the "x-forwarded-for" HTTP header if available, otherwise uses the remote
     * IP of requester.
     *
     * @param request <code>HttpRequestMessage</code>
     * @return <code>String</code> IP address
     */
    public static String getClientIP(HttpRequestInfo request)
    {
        final String xForwardedFor = request.getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_FOR);
        String clientIP;
        if (xForwardedFor == null) {
            clientIP = request.getClientIp();
        } else {
            clientIP = extractClientIpFromXForwardedFor(xForwardedFor);
        }
        return clientIP;
    }

    /**
     * Extract the client IP address from an x-forwarded-for header. Returns null if there is no x-forwarded-for header
     *
     * @param xForwardedFor a <code>String</code> value
     * @return a <code>String</code> value
     */
    public static String extractClientIpFromXForwardedFor(String xForwardedFor) {
        if (xForwardedFor == null) {
            return null;
        }
        xForwardedFor = xForwardedFor.trim();
        String tokenized[] = xForwardedFor.split(",");
        if (tokenized.length == 0) {
            return null;
        } else {
            return tokenized[0].trim();
        }
    }

    /**
     * return true if the client requested gzip content
     *
     * @param contentEncoding a <code>String</code> value
     * @return true if the content-encoding param containg gzip
     */
    public static boolean isGzipped(String contentEncoding) {
        return contentEncoding.contains("gzip");
    }

    public static boolean isGzipped(Headers headers) {
        String ce = headers.getFirst(HttpHeaderNames.CONTENT_ENCODING);
        return ce != null && isGzipped(ce);
    }

    public static boolean acceptsGzip(Headers headers) {
        String ae = headers.getFirst(HttpHeaderNames.ACCEPT_ENCODING);
        return ae != null && isGzipped(ae);
    }
}
