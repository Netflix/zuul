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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;


@RunWith(MockitoJUnitRunner.class)
public class RequestContextTest {
    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Test
    public void testGetContext() {
        RequestContext context = RequestContext.getCurrentContext();
        assertNotNull(context);
    }

    @Test
    public void testSetContextVariable() {
        RequestContext context = RequestContext.getCurrentContext();
        assertNotNull(context);
        context.set("test", "moo");
        assertEquals(context.get("test"), "moo");
    }

    @Test
    public void testSet() {
        RequestContext context = RequestContext.getCurrentContext();
        assertNotNull(context);
        context.set("test");
        assertEquals(context.get("test"), Boolean.TRUE);
    }

    @Test
    public void testBoolean() {
        RequestContext context = RequestContext.getCurrentContext();
        assertEquals(context.getBoolean("boolean_test"), Boolean.FALSE);
        assertEquals(context.getBoolean("boolean_test", true), true);

    }

    @Test
    public void testCopy() {
        RequestContext context = RequestContext.getCurrentContext();

        context.put("test", "test");
        context.put("test1", "test1");
        context.put("test2", "test2");

        RequestContext copy = context.copy();

        assertEquals(copy.get("test"), "test");
        assertEquals(copy.get("test1"), "test1");
        assertEquals(copy.get("test2"), "test2");
//            assertFalse(copy.get("test").hashCode() == com.netflix.zuul.context.get("test").hashCode());


    }


    @Test
    public void testResponseHeaders() {
        RequestContext context = RequestContext.getCurrentContext();
        context.addZuulRequestHeader("header", "test");
        Map headerMap = context.getZuulRequestHeaders();
        assertNotNull(headerMap);
        assertEquals(headerMap.get("header"), "test");
    }

    @Test
    public void testAccessors() {

        RequestContext context = new RequestContext();
        RequestContext.testSetCurrentContext(context);

        context.setRequest(request);
        context.setResponse(response);


        Throwable th = new Throwable();
        context.setThrowable(th);
        assertEquals(context.getThrowable(), th);

        assertEquals(context.debugRouting(), false);
        context.setDebugRouting(true);
        assertEquals(context.debugRouting(), true);

        assertEquals(context.debugRequest(), false);
        context.setDebugRequest(true);
        assertEquals(context.debugRequest(), true);

        context.setDebugRequest(false);
        assertEquals(context.debugRequest(), false);

        context.setDebugRouting(false);
        assertEquals(context.debugRouting(), false);


        try {
            URL url = new URL("http://www.moldfarm.com");
            context.setRouteHost(url);
            assertEquals(context.getRouteHost(), url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        InputStream in = mock(InputStream.class);
        context.setResponseDataStream(in);
        assertEquals(context.getResponseDataStream(), in);

        assertEquals(context.sendZuulResponse(), true);
        context.setSendZuulResponse(false);
        assertEquals(context.sendZuulResponse(), false);

        context.setResponseStatusCode(100);
        assertEquals(context.getResponseStatusCode(), 100);

    }

}