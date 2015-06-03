package com.netflix.zuul.util;

import com.netflix.zuul.constants.ZuulHeaders;
import com.netflix.zuul.context.Headers;
import com.netflix.zuul.context.HttpRequestMessage;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 11:05 PM
 */
public class HttpUtils
{
    public static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";


    /**
     * Get the IP address of client making the request.
     *
     * Uses the "x-forwarded-for" HTTP header if available, otherwise uses the remote
     * IP of requester.
     *
     * @param request <code>HttpRequestMessage</code>
     * @return <code>String</code> IP address
     */
    public static String getClientIP(HttpRequestMessage request)
    {
        final String xForwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR_HEADER);
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
        String ce = headers.getFirst(ZuulHeaders.CONTENT_ENCODING);
        return ce != null && isGzipped(ce);
    }

    public static boolean acceptsGzip(Headers headers) {
        String ae = headers.getFirst(ZuulHeaders.ACCEPT_ENCODING);
        return ae != null && isGzipped(ae);
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
    }
}
