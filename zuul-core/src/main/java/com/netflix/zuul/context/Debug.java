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
package com.netflix.zuul.context;

import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.*;
import com.netflix.zuul.util.HttpUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/**
 * Simple wrapper class around the RequestContext for setting and managing Request level Debug data.
 * @author Mikey Cohen
 * Date: 1/25/12
 * Time: 2:26 PM
 */
public class Debug {
    private static final Logger LOG = LoggerFactory.getLogger(Debug.class);

    public static void setDebugRequest(SessionContext ctx, boolean bDebug) {
        ctx.setDebugRequest(bDebug);
    }

    public static void setDebugRequestHeadersOnly(SessionContext ctx, boolean bHeadersOnly) {
        ctx.setDebugRequestHeadersOnly(bHeadersOnly);
    }

    public static boolean debugRequestHeadersOnly(SessionContext ctx) {
        return ctx.debugRequestHeadersOnly();
    }


    public static void setDebugRouting(SessionContext ctx, boolean bDebug) {
        ctx.setDebugRouting(bDebug);
    }


    public static boolean debugRequest(SessionContext ctx) {
        return ctx.debugRequest();
    }

    public static boolean debugRouting(SessionContext ctx) {
        return ctx.debugRouting();
    }

    public static void addRoutingDebug(SessionContext ctx, String line) {
        List<String> rd = getRoutingDebug(ctx);
        rd.add(line);
    }

    public static void addRequestDebugForMessage(SessionContext ctx, ZuulMessage message, String prefix)
    {
        for (Header header : message.getHeaders().entries()) {
            Debug.addRequestDebug(ctx, prefix + " " + header.getKey() + " " + header.getValue());
        }

        if (message.hasBody()) {
            String bodyStr = message.getBodyAsText();
            Debug.addRequestDebug(ctx, prefix + " " + bodyStr);
        }
    }

    /**
     *
     * @return Returns the list of routiong debug messages
     */
    public static List<String> getRoutingDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.get("routingDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            ctx.set("routingDebug", rd);
        }
        return rd;
    }

    /**
     * Adds a line to the  Request debug messages
     * @param line
     */
    public static void addRequestDebug(SessionContext ctx, String line) {
        List<String> rd = getRequestDebug(ctx);
        rd.add(line);
    }

    /**
     *
     * @return returns the list of request debug messages
     */
    public static List<String> getRequestDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.get("requestDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            ctx.set("requestDebug", rd);
        }
        return rd;
    }


    /**
     * Adds debug details about changes that a given filter made to the request context.
     * @param filterName
     * @param copy
     */
    public static void compareContextState(String filterName, SessionContext context, SessionContext copy) {
        // TODO - only comparing Attributes. Need to compare the messages too.
        Iterator<String> it = context.keySet().iterator();
        String key = it.next();
        while (key != null) {
            if ((!key.equals("routingDebug") && !key.equals("requestDebug"))) {
                Object newValue = context.get(key);
                Object oldValue = copy.get(key);
                if (oldValue == null && newValue != null) {
                    addRoutingDebug(context, "{" + filterName + "} added " + key + "=" + newValue.toString());
                } else if (oldValue != null && newValue != null) {
                    if (!(oldValue.equals(newValue))) {
                        addRoutingDebug(context, "{" +filterName + "} changed " + key + "=" + newValue.toString());
                    }
                }
            }
            if (it.hasNext()) {
                key = it.next();
            } else {
                key = null;
            }
        }

    }

    public static Observable<Boolean> writeDebugRequest(SessionContext context,
                                                                HttpRequestInfo request, boolean isInbound)
    {
        Observable<Boolean> obs = null;
        if (Debug.debugRequest(context)) {
            String prefix = isInbound ? "REQUEST_INBOUND" : "REQUEST_OUTBOUND";
            String arrow = ">";

            Debug.addRequestDebug(context, String.format("%s:: %s LINE: %s %s %s",
                    prefix, arrow, request.getMethod().toUpperCase(), request.getPathAndQuery(), request.getProtocol()));
            obs = Debug.writeDebugMessage(context, request, prefix, arrow);
        }

        if (obs == null)
            obs = Observable.just(Boolean.FALSE);

        return obs;
    }

    public static Observable<Boolean> writeDebugResponse(SessionContext context,
                                                                  HttpResponseInfo response, boolean isInbound)
    {
        Observable<Boolean> obs = null;
        if (Debug.debugRequest(context)) {
            String prefix = isInbound ? "RESPONSE_INBOUND" : "RESPONSE_OUTBOUND";
            String arrow = "<";

            Debug.addRequestDebug(context, String.format("%s:: %s STATUS: %s", prefix, arrow, response.getStatus()));
            obs = Debug.writeDebugMessage(context, response, prefix, arrow);
        }

        if (obs == null)
            obs = Observable.just(Boolean.FALSE);

        return obs;
    }

    public static Observable<Boolean> writeDebugMessage(SessionContext context, ZuulMessage msg,
                                                            String prefix, String arrow)
    {
        Observable<Boolean> obs = null;

        for (Header header : msg.getHeaders().entries()) {
            Debug.addRequestDebug(context, String.format("%s:: %s HDR: %s:%s", prefix, arrow, header.getKey(), header.getValue()));
        }

        // Capture the response body into a Byte array for later usage.
        if (msg.hasBody()) {
            if (! Debug.debugRequestHeadersOnly(context)) {
                // Convert body to a String and add to debug log.
                String body = msg.getBodyAsText();
                Debug.addRequestDebug(context, String.format("%s:: %s BODY: %s", prefix, arrow, body));
            }
        }

        if (obs == null)
            obs = Observable.just(Boolean.FALSE);

        return obs;
    }

    public static String bodyToText(byte[] bodyBytes, Headers headers)
    {
        try {
            if (HttpUtils.isGzipped(headers)) {
                GZIPInputStream gzIn = new GZIPInputStream(new ByteArrayInputStream(bodyBytes));
                bodyBytes = IOUtils.toByteArray(gzIn);
            }
            return IOUtils.toString(bodyBytes, "UTF-8");
        }
        catch (IOException e) {
            LOG.error("Error reading message body for debugging.", e);
            return "ERROR READING MESSAGE BODY!";
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
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
        public void testWriteInboundRequestDebug()
        {
            ctx.setDebugRequest(true);
            ctx.setDebugRequestHeadersOnly(true);
            Debug.writeDebugRequest(ctx, request, true).toBlocking().single();

            List<String> debugLines = Debug.getRequestDebug(ctx);
            assertEquals(3, debugLines.size());
            assertEquals("REQUEST_INBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
            assertEquals("REQUEST_INBOUND:: > HDR: Content-Length:13", debugLines.get(1));
            assertEquals("REQUEST_INBOUND:: > HDR: lah:deda", debugLines.get(2));
        }

        @Test
        public void testWriteOutboundRequestDebug()
        {
            ctx.setDebugRequest(true);
            ctx.setDebugRequestHeadersOnly(true);
            Debug.writeDebugRequest(ctx, request, false).toBlocking().single();

            List<String> debugLines = Debug.getRequestDebug(ctx);
            assertEquals(3, debugLines.size());
            assertEquals("REQUEST_OUTBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
            assertEquals("REQUEST_OUTBOUND:: > HDR: Content-Length:13", debugLines.get(1));
            assertEquals("REQUEST_OUTBOUND:: > HDR: lah:deda", debugLines.get(2));
        }

        @Test
        public void testWriteRequestDebug_WithBody()
        {
            ctx.setDebugRequest(true);
            ctx.setDebugRequestHeadersOnly(false);
            Debug.writeDebugRequest(ctx, request, true).toBlocking().single();

            List<String> debugLines = Debug.getRequestDebug(ctx);
            assertEquals(4, debugLines.size());
            assertEquals("REQUEST_INBOUND:: > LINE: POST /some/where?k1=v1 HTTP/1.1", debugLines.get(0));
            assertEquals("REQUEST_INBOUND:: > HDR: Content-Length:13", debugLines.get(1));
            assertEquals("REQUEST_INBOUND:: > HDR: lah:deda", debugLines.get(2));
            assertEquals("REQUEST_INBOUND:: > BODY: some text", debugLines.get(3));
        }

        @Test
        public void testWriteInboundResponseDebug()
        {
            ctx.setDebugRequest(true);
            ctx.setDebugRequestHeadersOnly(true);
            Debug.writeDebugResponse(ctx, response, true).toBlocking().single();

            List<String> debugLines = Debug.getRequestDebug(ctx);
            assertEquals(3, debugLines.size());
            assertEquals("RESPONSE_INBOUND:: < STATUS: 200", debugLines.get(0));
            assertEquals("RESPONSE_INBOUND:: < HDR: Content-Length:13", debugLines.get(1));
            assertEquals("RESPONSE_INBOUND:: < HDR: lah:deda", debugLines.get(2));
        }

        @Test
        public void testWriteOutboundResponseDebug()
        {
            ctx.setDebugRequest(true);
            ctx.setDebugRequestHeadersOnly(true);
            Debug.writeDebugResponse(ctx, response, false).toBlocking().single();

            List<String> debugLines = Debug.getRequestDebug(ctx);
            assertEquals(3, debugLines.size());
            assertEquals("RESPONSE_OUTBOUND:: < STATUS: 200", debugLines.get(0));
            assertEquals("RESPONSE_OUTBOUND:: < HDR: Content-Length:13", debugLines.get(1));
            assertEquals("RESPONSE_OUTBOUND:: < HDR: lah:deda", debugLines.get(2));
        }

        @Test
        public void testWriteResponseDebug_WithBody()
        {
            ctx.setDebugRequest(true);
            ctx.setDebugRequestHeadersOnly(false);
            Debug.writeDebugResponse(ctx, response, true).toBlocking().single();

            List<String> debugLines = Debug.getRequestDebug(ctx);
            assertEquals(4, debugLines.size());
            assertEquals("RESPONSE_INBOUND:: < STATUS: 200", debugLines.get(0));
            assertEquals("RESPONSE_INBOUND:: < HDR: Content-Length:13", debugLines.get(1));
            assertEquals("RESPONSE_INBOUND:: < HDR: lah:deda", debugLines.get(2));
            assertEquals("RESPONSE_INBOUND:: < BODY: response text", debugLines.get(3));
        }
    }
}
