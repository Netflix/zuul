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

import com.netflix.util.Pair;
import com.netflix.zuul.constants.ZuulHeaders;
import com.netflix.zuul.util.DeepCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Request Context holds request, response,  state information and data for ZuulFilters to access and share.
 * The RequestContext lives for the duration of the request and is ThreadLocal.
 * extensions of RequestContext can be substituted by setting the contextClass.
 * Most methods here are convenience wrapper methods; the RequestContext is an extension of a ConcurrentHashMap
 *
 * @author Mikey Cohen
 *         Date: 10/13/11
 *         Time: 10:21 AM
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
                throw new RuntimeException(e);
            }
        }
    };


    public RequestContext() {
        super();
    }

    /**
     * Override the default RequestContext
     *
     * @param clazz
     */
    public static void setContextClass(Class<? extends RequestContext> clazz) {
        contextClass = clazz;
    }

    /**
     * set an overriden "test" context
     *
     * @param context
     */
    public static void testSetCurrentContext(RequestContext context) {
        testContext = context;
    }

    /**
     * Get the current RequestContext
     *
     * @return the current RequestContext
     */
    public static RequestContext getCurrentContext() {
        if (testContext != null) return testContext;

        RequestContext context = threadLocal.get();
        return context;
    }

    /**
     * Convenience method to return a boolean value for a given key
     *
     * @param key
     * @return true or false depending what was set. default is false
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * Convenience method to return a boolean value for a given key
     *
     * @param key
     * @param defaultResponse
     * @return true or false depending what was set. default defaultResponse
     */
    public boolean getBoolean(String key, boolean defaultResponse) {
        Boolean b = (Boolean) get(key);
        if (b != null) {
            return b.booleanValue();
        }
        return defaultResponse;
    }

    /**
     * sets a key value to Boolen.TRUE
     *
     * @param key
     */
    public void set(String key) {
        put(key, Boolean.TRUE);
    }

    /**
     * puts the key, value into the map. a null value will remove the key from the map
     *
     * @param key
     * @param value
     */
    public void set(String key, Object value) {
        if (value != null) put(key, value);
        else remove(key);
    }

    /**
     * true if  zuulEngineRan
     *
     * @return
     */
    public boolean getZuulEngineRan() {
        return getBoolean("zuulEngineRan");
    }

    /**
     * sets zuulEngineRan to true
     */
    public void setZuulEngineRan() {
        put("zuulEngineRan", true);
    }

    /**
     * @return the HttpServletRequest from the "request" key
     */
    public HttpServletRequest getRequest() {
        return (HttpServletRequest) get("request");
    }

    /**
     * sets the HttpServletRequest into the "request" key
     *
     * @param request
     */
    public void setRequest(HttpServletRequest request) {
        put("request", request);
    }

    /**
     * @return the HttpServletResponse from the "response" key
     */
    public HttpServletResponse getResponse() {
        return (HttpServletResponse) get("response");
    }

    /**
     * sets the "response" key to the HttpServletResponse passed in
     *
     * @param response
     */
    public void setResponse(HttpServletResponse response) {
        set("response", response);
    }

    /**
     * returns a set throwable
     *
     * @return a set throwable
     */
    public Throwable getThrowable() {
        return (Throwable) get("throwable");

    }

    /**
     * sets a throwable
     *
     * @param th
     */
    public void setThrowable(Throwable th) {
        put("throwable", th);

    }

    /**
     * sets  debugRouting
     *
     * @param bDebug
     */
    public void setDebugRouting(boolean bDebug) {
        set("debugRouting", bDebug);
    }

    /**
     * @return "debugRouting"
     */
    public boolean debugRouting() {
        return getBoolean("debugRouting");
    }

    /**
     * sets "debugRequestHeadersOnly" to bHeadersOnly
     *
     * @param bHeadersOnly
     */
    public void setDebugRequestHeadersOnly(boolean bHeadersOnly) {
        set("debugRequestHeadersOnly", bHeadersOnly);

    }

    /**
     * @return "debugRequestHeadersOnly"
     */
    public boolean debugRequestHeadersOnly() {
        return getBoolean("debugRequestHeadersOnly");
    }

    /**
     * sets "debugRequest"
     *
     * @param bDebug
     */
    public void setDebugRequest(boolean bDebug) {
        set("debugRequest", bDebug);
    }

    /**
     * gets debugRequest
     *
     * @return debugRequest
     */
    public boolean debugRequest() {
        return getBoolean("debugRequest");
    }

    /**
     * removes "routeHost" key
     */
    public void removeRouteHost() {
        remove("routeHost");
    }

    /**
     * sets routeHost
     *
     * @param routeHost a URL
     */
    public void setRouteHost(URL routeHost) {
        set("routeHost", routeHost);
    }

    /**
     * @return "routeHost" URL
     */
    public URL getRouteHost() {
        return (URL) get("routeHost");
    }

    /**
     * appends filter name and status to the filter execution history for the
     * current request
     * 
     * @param name - name of the filter
     * @param status - status of the filter
     * @param time - time it took to execute the filter
     */
    public void addFilterExecutionSummary(String name, String status, long time) {
            StringBuilder sb = getFilterExecutionSummary();
            if (sb.length() > 0) sb.append(", ");
            sb.append(name).append('[').append(status).append(']').append('[').append(time).append("ms]");
    }

    /**
     * @return String that represents the filter execution history for the current request
     */
    public StringBuilder getFilterExecutionSummary() {
        if (get("executedFilters") == null) {
            putIfAbsent("executedFilters", new StringBuilder());
        }
        return (StringBuilder) get("executedFilters");
    }
    
    /**
     * sets the "responseBody" value as a String. This is the response sent back to the client.
     *
     * @param body
     */
    public void setResponseBody(String body) {
        set("responseBody", body);
    }

    /**
     * @return the String response body to be snt back to the requesting client
     */
    public String getResponseBody() {
        return (String) get("responseBody");
    }

    /**
     * sets the InputStream of the response into the responseDataStream
     *
     * @param responseDataStream
     */
    public void setResponseDataStream(InputStream responseDataStream) {
        set("responseDataStream", responseDataStream);
    }

    /**
     * sets the flag responseGZipped if the response is gzipped
     *
     * @param gzipped
     */
    public void setResponseGZipped(boolean gzipped) {
        put("responseGZipped", gzipped);
    }

    /**
     * @return true if responseGZipped is true (the response is gzipped)
     */
    public boolean getResponseGZipped() {
        return getBoolean("responseGZipped", true);
    }

    /**
     * @return the InputStream Response
     */
    public InputStream getResponseDataStream() {
        return (InputStream) get("responseDataStream");
    }

    /**
     * If this value is true then the response should be sent to the client.
     *
     * @return
     */
    public boolean sendZuulResponse() {
        return getBoolean("sendZuulResponse", true);
    }

    /**
     * sets the sendZuulResponse boolean
     *
     * @param bSend
     */
    public void setSendZuulResponse(boolean bSend) {
        set("sendZuulResponse", Boolean.valueOf(bSend));
    }

    /**
     * returns the response status code. Default is 200
     *
     * @return
     */
    public int getResponseStatusCode() {
        return get("responseStatusCode") != null ? (Integer) get("responseStatusCode") : 500;
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

    /**
     * add a header to be sent to the origin
     *
     * @param name
     * @param value
     */
    public void addZuulRequestHeader(String name, String value) {
        getZuulRequestHeaders().put(name.toLowerCase(), value);
    }

    /**
     * return the list of requestHeaders to be sent to the origin
     *
     * @return the list of requestHeaders to be sent to the origin
     */
    public Map<String, String> getZuulRequestHeaders() {
        if (get("zuulRequestHeaders") == null) {
            HashMap<String, String> zuulRequestHeaders = new HashMap<String, String>();
            putIfAbsent("zuulRequestHeaders", zuulRequestHeaders);
        }
        return (Map<String, String>) get("zuulRequestHeaders");
    }

    /**
     * add a header to be sent to the response
     *
     * @param name
     * @param value
     */
    public void addZuulResponseHeader(String name, String value) {
        getZuulResponseHeaders().add(new Pair<String, String>(name, value));
    }

    /**
     * returns the current response header list
     *
     * @return a List<Pair<String, String>>  of response headers
     */
    public List<Pair<String, String>> getZuulResponseHeaders() {
        if (get("zuulResponseHeaders") == null) {
            List<Pair<String, String>> zuulRequestHeaders = new ArrayList<Pair<String, String>>();
            putIfAbsent("zuulResponseHeaders", zuulRequestHeaders);
        }
        return (List<Pair<String, String>>) get("zuulResponseHeaders");
    }

    /**
     * the Origin response headers
     *
     * @return the List<Pair<String, String>> of headers sent back from the origin
     */
    public List<Pair<String, String>> getOriginResponseHeaders() {
        if (get("originResponseHeaders") == null) {
            List<Pair<String, String>> originResponseHeaders = new ArrayList<Pair<String, String>>();
            putIfAbsent("originResponseHeaders", originResponseHeaders);
        }
        return (List<Pair<String, String>>) get("originResponseHeaders");
    }

    /**
     * adds a header to the origin response headers
     *
     * @param name
     * @param value
     */
    public void addOriginResponseHeader(String name, String value) {
        getOriginResponseHeaders().add(new Pair<String, String>(name, value));
    }

    /**
     * returns the content-length of the origin response
     *
     * @return the content-length of the origin response
     */
    public Long getOriginContentLength() {
        return (Long) get("originContentLength");
    }

    /**
     * sets the content-length from the origin response
     *
     * @param v
     */
    public void setOriginContentLength(Long v) {
        set("originContentLength", v);
    }

    /**
     * sets the content-length from the origin response
     *
     * @param v parses the string into an int
     */
    public void setOriginContentLength(String v) {
        try {
            final Long i = Long.valueOf(v);
            set("originContentLength", i);
        } catch (NumberFormatException e) {
            LOG.warn("error parsing origin content length", e);
        }
    }

    /**
     * @return true if the request body is chunked
     */
    public boolean isChunkedRequestBody() {
        final Object v = get("chunkedRequestBody");
        return (v != null) ? (Boolean) v : false;
    }

    /**
     * sets chunkedRequestBody to true
     */
    public void setChunkedRequestBody() {
        this.set("chunkedRequestBody", Boolean.TRUE);
    }

    /**
     * @return true is the client request can accept gzip encoding. Checks the "accept-encoding" header
     */
    public boolean isGzipRequested() {
        final String requestEncoding = this.getRequest().getHeader(ZuulHeaders.ACCEPT_ENCODING);
        return requestEncoding != null && requestEncoding.toLowerCase().contains("gzip");
    }

    /**
     * unsets the threadLocal context. Done at the end of the request.
     */
    public void unset() {
        threadLocal.remove();
    }

    /**
     * Mkaes a copy of the RequestContext. This is used for debugging.
     *
     * @return
     */
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

    /**
     * @return Map<String, List<String>>  of the request Query Parameters
     */
    public Map<String, List<String>> getRequestQueryParams() {
        return (Map<String, List<String>>) get("requestQueryParams");
    }

    /**
     * sets the request query params list
     *
     * @param qp Map<String, List<String>> qp
     */
    public void setRequestQueryParams(Map<String, List<String>> qp) {
        put("requestQueryParams", qp);
    }
}
