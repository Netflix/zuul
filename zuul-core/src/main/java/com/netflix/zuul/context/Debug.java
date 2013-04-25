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
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Mikey Cohen
 * Date: 1/25/12
 * Time: 2:26 PM
 */
public class Debug {

    public static void setDebugRequest(boolean bDebug) {
        RequestContext.getCurrentContext().setDebugRequest(bDebug);
    }

    public static void setDebugRequestHeadersOnly(boolean bHeadersOnly) {
        RequestContext.getCurrentContext().setDebugRequestHeadersOnly(bHeadersOnly);
    }

    public static boolean debugRequestHeadersOnly() {
        return RequestContext.getCurrentContext().debugRequestHeadersOnly();
    }


    public static void setDebugProxy(boolean bDebug) {
        RequestContext.getCurrentContext().setDebugProxy(bDebug);
    }


    public static boolean debugRequest() {
        return RequestContext.getCurrentContext().debugRequest();
    }

    public static boolean debugProxy() {
        return RequestContext.getCurrentContext().debugProxy();
    }

    public static void addProxyDebug(String line) {
        List<String> rd = getProxyDebug();
        rd.add(line);
    }

    public static List<String> getProxyDebug() {
        List<String> rd = (List<String>) RequestContext.getCurrentContext().get("proxyDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            RequestContext.getCurrentContext().set("proxyDebug", rd);
        }
        return rd;
    }

    public static void addRequestDebug(String line) {
        List<String> rd = getRequestDebug();
        rd.add(line);
    }

    public static List<String> getRequestDebug() {
        List<String> rd = (List<String>) RequestContext.getCurrentContext().get("requestDebug");
        if (rd == null) {
            rd = new ArrayList<String>();
            RequestContext.getCurrentContext().set("requestDebug", rd);
        }
        return rd;
    }


    public static void compareProxyContextState(String filterName, RequestContext copy) {
        RequestContext context = RequestContext.getCurrentContext();
        Iterator<String> it = context.keySet().iterator();
        String key = it.next();
        while (key != null) {
            if ((!key.equals("proxyDebug") && !key.equals("requestDebug"))) {
                Object newValue = context.get(key);
                Object oldValue = copy.get(key);
                if (oldValue == null && newValue != null) {
                    addProxyDebug("{" + filterName + "} added " + key + "=" + newValue.toString());
                } else if (oldValue != null && newValue != null) {
                    if (!(oldValue.equals(newValue))) {
                        addProxyDebug("{" +filterName + "} changed " + key + "=" + newValue.toString());
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
    public static class UnitTest {

        @Test
        public void testRequestDebug() {
            assertFalse(debugProxy());
            assertFalse(debugRequest());
            setDebugProxy(true);
            setDebugRequest(true);
            assertTrue(debugProxy());
            assertTrue(debugRequest());

            addProxyDebug("test1");
            assertTrue(getProxyDebug().contains("test1"));

            addRequestDebug("test2");
            assertTrue(getRequestDebug().contains("test2"));


        }
    }

}
