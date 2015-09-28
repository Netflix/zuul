package com.netflix.zuul.ribbon;

import com.netflix.client.http.CaseInsensitiveMultiMap;
import com.netflix.client.http.HttpResponse;
import com.netflix.zuul.bytebuf.ByteBufUtils;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class RibbonOriginTest {
    @Mock
    HttpResponse proxyResp;

    @Mock
    HttpRequestMessage request;

    @Test
    public void testSetResponse() throws Exception
    {
        RibbonOrigin origin = new RibbonOrigin("blah");
        origin = Mockito.spy(origin);

        CaseInsensitiveMultiMap headers = new CaseInsensitiveMultiMap();
        headers.addHeader("test", "test");
        headers.addHeader("content-length", "100");

        byte[] body = "test-body".getBytes("UTF-8");
        InputStream inp = new ByteArrayInputStream(body);

        Mockito.when(proxyResp.getStatus()).thenReturn(200);
        Mockito.when(proxyResp.getInputStream()).thenReturn(inp);
        Mockito.when(proxyResp.hasEntity()).thenReturn(true);
        Mockito.when(proxyResp.getHttpHeaders()).thenReturn(headers);
        SessionContext ctx = new SessionContext(new RibbonOriginManager());
        Mockito.when(request.getContext()).thenReturn(ctx);

        HttpResponseMessage response = origin.createHttpResponseMessage(proxyResp, request);

        Assert.assertEquals(200, response.getStatus());

        byte[] respBodyBytes = ByteBufUtils.toBytes(response.getBodyStream().toBlocking().single());
        Assert.assertNotNull(respBodyBytes);
        Assert.assertEquals(body.length, respBodyBytes.length);

        Assert.assertTrue(response.getHeaders().contains("test", "test"));
    }
}