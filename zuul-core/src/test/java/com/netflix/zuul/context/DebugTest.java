/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.zuul.context;

import static com.netflix.zuul.context.Debug.addRequestDebug;
import static com.netflix.zuul.context.Debug.addRoutingDebug;
import static com.netflix.zuul.context.Debug.debugRequest;
import static com.netflix.zuul.context.Debug.debugRouting;
import static com.netflix.zuul.context.Debug.getRequestDebug;
import static com.netflix.zuul.context.Debug.getRoutingDebug;
import static com.netflix.zuul.context.Debug.setDebugRequest;
import static com.netflix.zuul.context.Debug.setDebugRouting;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import java.net.SocketAddress;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DebugTest {

    private SessionContext ctx;
    private Headers headers;
    private HttpQueryParams params;
    private HttpRequestMessage request;
    private HttpResponseMessage response;

    @Before
    public void setup() {
        ctx = new SessionContext();

        headers = new Headers();
        headers.add("lah", "deda");

        params = new HttpQueryParams();
        params.add("k1", "v1");

        request = new HttpRequestMessageImpl(ctx, "HTTP/1.1", "post", "/some/where",
                params, headers, "9.9.9.9", "https", 80, "localhost");
        request.setBodyAsText("some text");
        request.storeInboundRequest();

        response = new HttpResponseMessageImpl(ctx, headers, request, 200);
        response.setBodyAsText("response text");
    }

    @Test
    public void testRequestDebug() {
        assertFalse(debugRouting(ctx));
        assertFalse(debugRequest(ctx));
        setDebugRouting(ctx, true);
        setDebugRequest(ctx, true);
        assertTrue(debugRouting(ctx));
        assertTrue(debugRequest(ctx));

        addRoutingDebug(ctx, "test1");
        assertTrue(getRoutingDebug(ctx).contains("test1"));

        addRequestDebug(ctx, "test2");
        assertTrue(getRequestDebug(ctx).contains("test2"));
    }

    @Test
    public void testWriteInboundRequestDebug() {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugRequest(ctx, request, true).toBlocking().single();

        List<String> debugLines = getRequestDebug(ctx);
        assertEquals(3, debugLines.size());
        assertEquals("REQUEST_INBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
        assertEquals("REQUEST_INBOUND:: > HDR: Content-Length:13", debugLines.get(1));
        assertEquals("REQUEST_INBOUND:: > HDR: lah:deda", debugLines.get(2));
    }

    @Test
    public void testWriteOutboundRequestDebug() {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugRequest(ctx, request, false).toBlocking().single();

        List<String> debugLines = getRequestDebug(ctx);
        assertEquals(3, debugLines.size());
        assertEquals("REQUEST_OUTBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
        assertEquals("REQUEST_OUTBOUND:: > HDR: Content-Length:13", debugLines.get(1));
        assertEquals("REQUEST_OUTBOUND:: > HDR: lah:deda", debugLines.get(2));
    }

    @Test
    public void testWriteRequestDebug_WithBody() {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(false);
        Debug.writeDebugRequest(ctx, request, true).toBlocking().single();

        List<String> debugLines = getRequestDebug(ctx);
        assertEquals(4, debugLines.size());
        assertEquals("REQUEST_INBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
        assertEquals("REQUEST_INBOUND:: > HDR: Content-Length:13", debugLines.get(1));
        assertEquals("REQUEST_INBOUND:: > HDR: lah:deda", debugLines.get(2));
        assertEquals("REQUEST_INBOUND:: > BODY: some text", debugLines.get(3));
    }

    @Test
    public void testWriteInboundResponseDebug() {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugResponse(ctx, response, true).toBlocking().single();

        List<String> debugLines = getRequestDebug(ctx);
        assertEquals(3, debugLines.size());
        assertEquals("RESPONSE_INBOUND:: < STATUS: 200", debugLines.get(0));
        assertEquals("RESPONSE_INBOUND:: < HDR: Content-Length:13", debugLines.get(1));
        assertEquals("RESPONSE_INBOUND:: < HDR: lah:deda", debugLines.get(2));
    }

    @Test
    public void testWriteOutboundResponseDebug() {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(true);
        Debug.writeDebugResponse(ctx, response, false).toBlocking().single();

        List<String> debugLines = getRequestDebug(ctx);
        assertEquals(3, debugLines.size());
        assertEquals("RESPONSE_OUTBOUND:: < STATUS: 200", debugLines.get(0));
        assertEquals("RESPONSE_OUTBOUND:: < HDR: Content-Length:13", debugLines.get(1));
        assertEquals("RESPONSE_OUTBOUND:: < HDR: lah:deda", debugLines.get(2));
    }

    @Test
    public void testWriteResponseDebug_WithBody() {
        ctx.setDebugRequest(true);
        ctx.setDebugRequestHeadersOnly(false);
        Debug.writeDebugResponse(ctx, response, true).toBlocking().single();

        List<String> debugLines = getRequestDebug(ctx);
        assertEquals(4, debugLines.size());
        assertEquals("RESPONSE_INBOUND:: < STATUS: 200", debugLines.get(0));
        assertEquals("RESPONSE_INBOUND:: < HDR: Content-Length:13", debugLines.get(1));
        assertEquals("RESPONSE_INBOUND:: < HDR: lah:deda", debugLines.get(2));
        assertEquals("RESPONSE_INBOUND:: < BODY: response text", debugLines.get(3));
    }
}
