package com.netflix.zuul.util;

import com.netflix.client.http.HttpResponse;
import com.netflix.zuul.message.HeaderName;
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
    private static final Set<HeaderName> HEADERS_TO_STRIP = new HashSet<>();
    static {
        HEADERS_TO_STRIP.add(HttpHeaderNames.CONNECTION);
        HEADERS_TO_STRIP.add(HttpHeaderNames.TRANSFER_ENCODING);
        HEADERS_TO_STRIP.add(HttpHeaderNames.KEEP_ALIVE);
    }

    public static boolean isValidRequestHeader(HeaderName headerName)
    {
        return ! HEADERS_TO_STRIP.contains(headerName);
    }

    public static boolean isValidResponseHeader(HeaderName headerName)
    {
        return ! HEADERS_TO_STRIP.contains(headerName);
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
