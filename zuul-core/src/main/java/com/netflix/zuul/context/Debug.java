/*
 * Copyright 2013 Netflix, Inc.
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

import com.netflix.zuul.util.HttpUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Simple wrapper class around the RequestContext for setting and managing Request level Debug data.
 * @author Mikey Cohen
 * Date: 1/25/12
 * Time: 2:26 PM
 */
public class Debug {
    private static final Logger LOG = LoggerFactory.getLogger(Debug.class);

    public static void setDebugRequest(SessionContext ctx, boolean bDebug) {
        ctx.getAttributes().setDebugRequest(bDebug);
    }

    public static void setDebugRequestHeadersOnly(SessionContext ctx, boolean bHeadersOnly) {
        ctx.getAttributes().setDebugRequestHeadersOnly(bHeadersOnly);
    }

    public static boolean debugRequestHeadersOnly(SessionContext ctx) {
        return ctx.getAttributes().debugRequestHeadersOnly();
    }


    public static void setDebugRouting(SessionContext ctx, boolean bDebug) {
        ctx.getAttributes().setDebugRouting(bDebug);
    }


    public static boolean debugRequest(SessionContext ctx) {
        return ctx.getAttributes().debugRequest();
    }

    public static boolean debugRouting(SessionContext ctx) {
        return ctx.getAttributes().debugRouting();
    }

    public static void addRoutingDebug(SessionContext ctx, String line) {
        List<String> rd = getRoutingDebug(ctx);
        rd.add(line);
    }

    public static void addRequestDebugForMessage(SessionContext ctx, ZuulMessage message, String prefix)
    {
        try {
            for (Map.Entry<String, String> header : message.getHeaders().entries()) {
                Debug.addRequestDebug(ctx, prefix + " " + header.getKey() + " " + header.getValue());
            }

            if (message.getBody() != null) {
                String bodyStr = new String(message.getBody(), "UTF-8");
                Debug.addRequestDebug(ctx, prefix + " " + bodyStr);
            }
        }
        catch (UnsupportedEncodingException e) {
            LOG.warn("Error writing message to debug log.", e);
        }
    }

    /**
     *
     * @return Returns the list of routiong debug messages
     */
    public static List<String> getRoutingDebug(SessionContext ctx) {
        List<String> rd = (List<String>) ctx.getAttributes().get("routingDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            ctx.getAttributes().set("routingDebug", rd);
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
        List<String> rd = (List<String>) ctx.getAttributes().get("requestDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            ctx.getAttributes().set("requestDebug", rd);
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
        Iterator<String> it = context.getAttributes().keySet().iterator();
        String key = it.next();
        while (key != null) {
            if ((!key.equals("routingDebug") && !key.equals("requestDebug"))) {
                Object newValue = context.getAttributes().get(key);
                Object oldValue = copy.getAttributes().get(key);
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


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
        @Mock
        private SessionContext ctx;

        @Before
        public void setup() {
            when(ctx.getAttributes()).thenReturn(new Attributes());
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
    }

}
