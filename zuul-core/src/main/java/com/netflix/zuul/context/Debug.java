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
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 1/25/12
 * Time: 2:26 PM
 * To change this template use File | Settings | File Templates.
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
