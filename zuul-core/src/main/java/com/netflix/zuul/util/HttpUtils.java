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

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestInfo;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 11:05 PM
 */
public class HttpUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
    private static final char[] MALICIOUS_HEADER_CHARS = {'\r', '\n'};

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

    /**
     * Ensure decoded new lines are not propagated in headers, in order to prevent XSS
     *
     * @param input - decoded header string
     * @return - clean header string
     */
    public static String stripMaliciousHeaderChars(String input) 
    {
        for (char c : MALICIOUS_HEADER_CHARS) {
            input = StringUtils.remove(input, c);
        }
        return input;
    }


    public static boolean hasNonZeroContentLengthHeader(ZuulMessage msg)
    {
        final Integer contentLengthVal = getContentLengthIfPresent(msg);
        return (contentLengthVal != null) && (contentLengthVal.intValue() > 0);
    }

    public static Integer getContentLengthIfPresent(ZuulMessage msg)
    {
        final String contentLengthValue = msg.getHeaders().getFirst(com.netflix.zuul.message.http.HttpHeaderNames.CONTENT_LENGTH);
        if (StringUtils.isNotEmpty(contentLengthValue) && StringUtils.isNumeric(contentLengthValue)) {
            try {
                return Integer.valueOf(contentLengthValue);
            }
            catch (NumberFormatException e) {
                LOG.info("Invalid Content-Length header value on request. " +
                        "value = " + String.valueOf(contentLengthValue));
            }
        }
        return null;
    }

    public static Integer getBodySizeIfKnown(ZuulMessage msg) {
        final Integer bodySize = getContentLengthIfPresent(msg);
        if (bodySize != null) {
            return bodySize.intValue();
        }
        if (msg.hasCompleteBody()) {
            return msg.getBodyLength();
        }
        return null;
    }

    public static boolean hasChunkedTransferEncodingHeader(ZuulMessage msg)
    {
        boolean isChunked = false;
        String teValue = msg.getHeaders().getFirst(com.netflix.zuul.message.http.HttpHeaderNames.TRANSFER_ENCODING);
        if (StringUtils.isNotEmpty(teValue)) {
            isChunked = "chunked".equals(teValue.toLowerCase());
        }
        return isChunked;
    }


    public static class UnitTest {

        @Test
        public void detectsGzip() {
            assertTrue(HttpUtils.isGzipped("gzip"));
        }

        @Test
        public void detectsNonGzip() {
            assertFalse(HttpUtils.isGzipped("identity"));
        }

        @Test
        public void detectsGzipAmongOtherEncodings() {
            assertTrue(HttpUtils.isGzipped("gzip, deflate"));
        }

        @Test
        public void stripMaliciousHeaderChars() {
            assertEquals("something", HttpUtils.stripMaliciousHeaderChars("some\r\nthing"));
            assertEquals("some thing", HttpUtils.stripMaliciousHeaderChars("some thing"));
            assertEquals("something", HttpUtils.stripMaliciousHeaderChars("\nsome\r\nthing\r"));
            assertEquals("", HttpUtils.stripMaliciousHeaderChars("\r"));
            assertEquals("", HttpUtils.stripMaliciousHeaderChars(""));
            assertNull(HttpUtils.stripMaliciousHeaderChars(null));
        }
    }
}
