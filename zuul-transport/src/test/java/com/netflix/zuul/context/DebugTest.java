package com.netflix.zuul.context;

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.origins.OriginManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.*;

public class DebugTest {
    private SessionContext ctx;
    private Headers headers;
    private HttpQueryParams params;
    private HttpRequestMessage request;
    private HttpResponseMessage response;

    @Before
    public void setup() {
        ctx = new SessionContext(Mockito.mock(OriginManager.class));

        headers = new Headers();
        headers.add("lah", "deda");

        params = new HttpQueryParams();
        params.add("k1", "v1");

        request = new HttpRequestMessageImpl(ctx, "HTTP/1.1", "post", "/some/where",
                                             params, headers, "9.9.9.9", "https", 80, "localhost");
        request.setBodyAsText("some text");

        response = new HttpResponseMessageImpl(ctx, headers, request, 200);
        response.setBodyAsText("response text");
    }

    @Test
    public void testRequestDebug() {
        assertFalse(Debug.debugRouting(ctx));
        assertFalse(Debug.debugRequest(ctx));
        Debug.setDebugRouting(ctx, true);
        Debug.setDebugRequest(ctx, true);
        assertTrue(Debug.debugRouting(ctx));
        assertTrue(Debug.debugRequest(ctx));

        Debug.addRoutingDebug(ctx, "test1");
        assertTrue(Debug.getRoutingDebug(ctx).contains("test1"));

        Debug.addRequestDebug(ctx, "test2");
        assertTrue(Debug.getRequestDebug(ctx).contains("test2"));
    }

    @Test
    public void testWriteInboundRequestDebug()
    {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugRequest(ctx, request, true).toBlocking().single();

        List<String> debugLines = Debug.getRequestDebug(ctx);
        assertEquals(2, debugLines.size());
        assertEquals("REQUEST_INBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
        assertEquals("REQUEST_INBOUND:: > HDR: lah:deda", debugLines.get(1));
    }

    @Test
    public void testWriteOutboundRequestDebug()
    {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugRequest(ctx, request, false).toBlocking().single();

        List<String> debugLines = Debug.getRequestDebug(ctx);
        assertEquals(2, debugLines.size());
        assertEquals("REQUEST_OUTBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
        assertEquals("REQUEST_OUTBOUND:: > HDR: lah:deda", debugLines.get(1));
    }

    @Test
    public void testWriteRequestDebug_WithBody()
    {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(false);
        Debug.writeDebugRequest(ctx, request, true).toBlocking().single();

        List<String> debugLines = Debug.getRequestDebug(ctx);
        assertEquals(3, debugLines.size());
        assertEquals("REQUEST_INBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
        assertEquals("REQUEST_INBOUND:: > HDR: lah:deda", debugLines.get(1));
        assertEquals("REQUEST_INBOUND:: > BODY: some text", debugLines.get(2));
    }

    @Test
    public void testWriteInboundResponseDebug()
    {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugResponse(ctx, response, true).toBlocking().single();

        List<String> debugLines = Debug.getRequestDebug(ctx);
        assertEquals(2, debugLines.size());
        assertEquals("RESPONSE_INBOUND:: < STATUS: 200", debugLines.get(0));
        assertEquals("RESPONSE_INBOUND:: < HDR: lah:deda", debugLines.get(1));
    }

    @Test
    public void testWriteOutboundResponseDebug()
    {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugResponse(ctx, response, false).toBlocking().single();

        List<String> debugLines = Debug.getRequestDebug(ctx);
        assertEquals(2, debugLines.size());
        assertEquals("RESPONSE_OUTBOUND:: < STATUS: 200", debugLines.get(0));
        assertEquals("RESPONSE_OUTBOUND:: < HDR: lah:deda", debugLines.get(1));
    }

    @Test
    public void testWriteResponseDebug_WithBody()
    {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(false);
        Debug.writeDebugResponse(ctx, response, true).toBlocking().single();

        List<String> debugLines = Debug.getRequestDebug(ctx);
        assertEquals(3, debugLines.size());
        assertEquals("RESPONSE_INBOUND:: < STATUS: 200", debugLines.get(0));
        assertEquals("RESPONSE_INBOUND:: < HDR: lah:deda", debugLines.get(1));
        assertEquals("RESPONSE_INBOUND:: < BODY: response text", debugLines.get(2));
    }
}