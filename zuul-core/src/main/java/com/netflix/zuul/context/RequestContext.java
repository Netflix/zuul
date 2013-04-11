package com.netflix.zuul.context;


import com.netflix.zuul.util.DeepCopy;
import com.netflix.util.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/13/11
 * Time: 10:21 AM
 * To change this template use File | Settings | File Templates.
 */
public class RequestContext extends ConcurrentHashMap<String, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(RequestContext.class);

    protected static Class<? extends RequestContext> contextClass = RequestContext.class;

    private static RequestContext testContext = null;

    protected static final ThreadLocal<? extends RequestContext> threadLocal = new ThreadLocal<RequestContext>() {

        @Override
        protected RequestContext initialValue() {
            try {
                return contextClass.newInstance();
            } catch (Throwable e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    };


    public RequestContext() {
        super();

    }

    public static void setContextClass(Class<? extends RequestContext> clazz) {
        contextClass = clazz;
    }

    public static void testSetCurrentContext(RequestContext context) {
        testContext = context;
    }

    public static RequestContext getCurrentContext() {
        if (testContext != null) return testContext;

        RequestContext context = threadLocal.get();
        return context;
    }


    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultResponse) {
        Boolean b = (Boolean) get(key);
        if (b != null) {
            return b.booleanValue();
        }
        return defaultResponse;
    }


    public void set(String key) {
        put(key, Boolean.TRUE);
    }

    public void set(String key, Object value) {
        if (value != null) put(key, value);
        else remove(key);
    }

    public boolean getProxyEngineRan() {
        return getBoolean("proxyEngineRan");
    }

    public void setProxyEngineRan() {
        put("proxyEngineRan", true);
    }

    public HttpServletRequest getRequest() {
        return (HttpServletRequest) get("request");
    }

    public void setRequest(HttpServletRequest request) {
        put("request", request);
    }

    public HttpServletResponse getResponse() {
        return (HttpServletResponse) get("response");
    }

    public void setResponse(HttpServletResponse response) {
        set("response", response);
    }

    public Throwable getThrowable() {
        return (Throwable) get("throwable");

    }

    public void setThrowable(Throwable th) {
        put("throwable", th);

    }

    public void setDebugProxy(boolean bDebug) {
        set("debugProxy", bDebug);
    }

    public boolean debugProxy() {
        return getBoolean("debugProxy");
    }

    public void setDebugRequestHeadersOnly(boolean bHeadersOnly) {
        set("debugRequestHeadersOnly", bHeadersOnly);

    }

    public boolean debugRequestHeadersOnly() {
        return getBoolean("debugRequestHeadersOnly");
    }


    public void setDebugRequest(boolean bDebug) {
        set("debugRequest", bDebug);
    }

    public boolean debugRequest() {
        return getBoolean("debugRequest");
    }

    public void removeProxyHost() {
        remove("proxyHost");
    }

    public void setProxyHost(URL proxyHost) {
        set("proxyHost", proxyHost);
    }

    public URL getProxyHost() {
        return (URL) get("proxyHost");
    }


    public void setResponseBody(String body) {
        set("responseBody", body);
    }

    public String getResponseBody() {
        return (String) get("responseBody");
    }

    public void setProxyResponseDataStream(InputStream proxyResponseDataStream) {
        set("proxyResponseDataStream", proxyResponseDataStream);
    }

    public void setProxyResponseGZipped(boolean gzipped) {
        put("proxyResponseGZipped", gzipped);
    }

    public boolean getProxyResponseGZipped() {
        return getBoolean("proxyResponseGZipped", true);
    }

    public InputStream getProxyResponseDataStream() {
        return (InputStream) get("proxyResponseDataStream");
    }

    public boolean sendProxyResponse() {
        return getBoolean("sendProxyResponse", true);
    }

    public void setSendProxyResponse(boolean bSend) {
        set("sendProxyResponse", Boolean.valueOf(bSend));
    }


    public int getResponseStatusCode() {
        return get("responseStatusCode") != null ? (Integer) get("responseStatusCode") : 200;
    }


    /**
     * Use this instead of response.setStatusCode()
     *
     * @param nStatusCode
     */
    public void setResponseStatusCode(int nStatusCode) {
        getResponse().setStatus(nStatusCode);
        set("responseStatusCode", nStatusCode);
    }

    public void addProxyRequestHeader(String name, String value) {
        getProxyRequestHeaders().put(name.toLowerCase(), value);
    }

    public Map<String, String> getProxyRequestHeaders() {
        if (get("proxyRequestHeaders") == null) {
            HashMap<String, String> proxyRequestHeaders = new HashMap<String, String>();
            putIfAbsent("proxyRequestHeaders", proxyRequestHeaders);
        }
        return (Map<String, String>) get("proxyRequestHeaders");
    }

    public void addProxyResponseHeader(String name, String value) {
        getProxyResponseHeaders().add(new Pair<String, String>(name, value));
    }

    public List<Pair<String, String>> getProxyResponseHeaders() {
        if (get("proxyResponseHeaders") == null) {
            List<Pair<String, String>> proxyRequestHeaders = new ArrayList<Pair<String, String>>();
            putIfAbsent("proxyResponseHeaders", proxyRequestHeaders);
        }
        return (List<Pair<String, String>>) get("proxyResponseHeaders");
    }

    public List<Pair<String, String>> getOriginResponseHeaders() {
        if (get("originResponseHeaders") == null) {
            List<Pair<String, String>> originResponseHeaders = new ArrayList<Pair<String, String>>();
            putIfAbsent("originResponseHeaders", originResponseHeaders);
        }
        return (List<Pair<String, String>>) get("originResponseHeaders");
    }

    public void addOriginResponseHeader(String name, String value) {
        getOriginResponseHeaders().add(new Pair<String, String>(name, value));
    }

    public Integer getOriginContentLength() {
        return (Integer) get("originContentLength");
    }

    public void setOriginContentLength(Integer v) {
        set("originContentLength", v);
    }

    public void setOriginContentLength(String v) {
        try {
            final Integer i = Integer.valueOf(v);
            set("originContentLength", i);
        } catch (NumberFormatException e) {
            LOG.warn("error parsing origin content length", e);
        }
    }

    public boolean isChunkedRequestBody() {
        final Object v = get("chunkedRequestBody");
        return (v != null) ? (Boolean) v : false;
    }

    public void setChunkedRequestBody() {
        this.set("chunkedRequestBody", Boolean.TRUE);
    }

    public boolean isGzipRequested() {
        final String requestEncoding = this.getRequest().getHeader("accept-encoding");
        return requestEncoding != null && requestEncoding.toLowerCase().contains("gzip");
    }

    public void unset() {
        threadLocal.remove();
    }

    public RequestContext copy() {
        RequestContext copy = new RequestContext();
        Iterator<String> it = keySet().iterator();
        String key = it.next();
        while (key != null) {
            Object orig = get(key);
            try {
                Object copyValue = DeepCopy.copy(orig);
                if (copyValue != null) {
                    copy.set(key, copyValue);
                } else {
                    copy.set(key, orig);
                }
            } catch (NotSerializableException e) {
                copy.set(key, orig);
            }
            if (it.hasNext()) {
                key = it.next();
            } else {
                key = null;
            }
        }
        return copy;
    }

    public Map<String, List<String>> getRequestQueryParams() {
        return (Map<String, List<String>>) get("requestQueryParams");
    }

    public void setRequestQueryParams(Map<String, List<String>> qp) {
        put("requestQueryParams", qp);
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {
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
//            assertFalse(copy.get("test").hashCode() == context.get("test").hashCode());


        }


        @Test
        public void testResponseHeaders() {
            RequestContext context = RequestContext.getCurrentContext();
            context.addProxyRequestHeader("header", "test");
            Map headerMap = context.getProxyRequestHeaders();
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

            assertEquals(context.debugProxy(), false);
            context.setDebugProxy(true);
            assertEquals(context.debugProxy(), true);

            assertEquals(context.debugRequest(), false);
            context.setDebugRequest(true);
            assertEquals(context.debugRequest(), true);

            context.setDebugRequest(false);
            assertEquals(context.debugRequest(), false);

            context.setDebugProxy(false);
            assertEquals(context.debugProxy(), false);


            try {
                URL url = new URL("http://www.moldfarm.com");
                context.setProxyHost(url);
                assertEquals(context.getProxyHost(), url);
            } catch (MalformedURLException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            InputStream in = mock(InputStream.class);
            context.setProxyResponseDataStream(in);
            assertEquals(context.getProxyResponseDataStream(), in);

            assertEquals(context.sendProxyResponse(), true);
            context.setSendProxyResponse(false);
            assertEquals(context.sendProxyResponse(), false);

            context.setResponseStatusCode(100);
            assertEquals(context.getResponseStatusCode(), 100);

        }

    }

}
