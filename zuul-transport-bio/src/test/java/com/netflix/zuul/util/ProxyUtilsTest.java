package com.netflix.zuul.util;

import com.netflix.client.http.HttpResponse;
import com.netflix.zuul.message.http.HttpRequestMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ProxyUtilsTest {
    @Mock
    HttpResponse proxyResp;

    @Mock
    HttpRequestMessage request;

    @Test
    public void testIsValidResponseHeader()
    {
        Assert.assertTrue(ProxyUtils.isValidResponseHeader("test"));
        Assert.assertFalse(ProxyUtils.isValidResponseHeader("content-length"));
        Assert.assertFalse(ProxyUtils.isValidResponseHeader("connection"));
    }
}