package com.netflix.zuul.util;

import com.netflix.client.http.HttpResponse;
import com.netflix.zuul.message.http.HttpRequestMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * User: michaels@netflix.com
 * Date: 6/8/15
 * Time: 11:50 AM
 */
public class ProxyUtils
{
    public static boolean isValidRequestHeader(String headerName)
    {
        switch (headerName.toLowerCase()) {
            case "connection":
            case "content-length":
            case "transfer-encoding":
                return false;
            default:
                return true;
        }
    }

    public static boolean isValidResponseHeader(String headerName)
    {
        switch (headerName.toLowerCase()) {
            case "connection":
            case "keep-alive":
            case "content-length":
            case "server":
            case "transfer-encoding":
                return false;
            default:
                return true;
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
            Assert.assertTrue(isValidResponseHeader("test"));
            Assert.assertFalse(isValidResponseHeader("content-length"));
            Assert.assertFalse(isValidResponseHeader("connection"));
        }
    }
}
