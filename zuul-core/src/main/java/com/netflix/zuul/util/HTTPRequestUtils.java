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
package com.netflix.zuul.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.netflix.zuul.context.RequestContext;

/**
 * Some handy methods for workign with HTTP requests
 * @author Mikey Cohen
 * Date: 2/10/12
 * Time: 8:22 AM
 */
public class HTTPRequestUtils {

    private final static HTTPRequestUtils INSTANCE = new HTTPRequestUtils();

    public static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";


    /**
     * Get the IP address of client making the request.
     *
     * Uses the "x-forwarded-for" HTTP header if available, otherwise uses the remote
     * IP of requester.
     *
     * @param request <code>HttpServletRequest</code>
     * @return <code>String</code> IP address
     */
    public String getClientIP(HttpServletRequest request) {
        final String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        String clientIP = null;
        if (xForwardedFor == null) {
            clientIP = request.getRemoteAddr();
        } else {
            clientIP = extractClientIpFromXForwardedFor(xForwardedFor);
        }
        return clientIP;
    }

    /**
     * Extract the client IP address from an x-forwarded-for header. Returns null if there is no x-forwarded-for header
     *
     * @param xForwardedFor a <code>String</code> value
     * @return a <code>String</code> value
     */
    public final String extractClientIpFromXForwardedFor(String xForwardedFor) {
        if (xForwardedFor == null) {
            return null;
        }
        xForwardedFor = xForwardedFor.trim();
        String tokenized[] = xForwardedFor.split(",");
        if (tokenized.length == 0) {
            return null;
        } else {
            return tokenized[0].trim();
        }
    }

    /**
     * return singleton HTTPRequestUtils object
     *
     * @return a <code>HTTPRequestUtils</code> value
     */
    public static HTTPRequestUtils getInstance() {
        return INSTANCE;
    }

    /**
     * returns the Header value for the given sHeaderName
     *
     * @param sHeaderName a <code>String</code> value
     * @return a <code>String</code> value
     */
    public String getHeaderValue(String sHeaderName) {
        return RequestContext.getCurrentContext().getRequest().getHeader(sHeaderName);
    }

    /**
     * returns a form value from a given sHeaderName
     *
     * @param sHeaderName a <code>String</code> value
     * @return a <code>String</code> value
     */
    public String getFormValue(String sHeaderName) {
        return RequestContext.getCurrentContext().getRequest().getParameter(sHeaderName);
    }

    /**
     * returns headers as a Map with String keys and Lists of Strings as values
     * @return
     */

    public Map<String, List<String>> getRequestHeaderMap() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();
        Map<String,List<String>> headers = new HashMap<String,List<String>>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if(headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value = request.getHeader(name);

                if(name != null && !name.isEmpty() && value != null) {
                    List<String> valueList = new ArrayList<String>();
                    if(headers.containsKey(name)) {
                        headers.get(name).add(value);
                    }
                    valueList.add(value);
                    headers.put(name, valueList);
                }
            }
        }
        return Collections.unmodifiableMap(headers);

    }

    /**
     * returns query params as a Map with String keys and Lists of Strings as values
     * @return
     */
    public Map<String, List<String>> getQueryParams() {

        Map<String, List<String>> qp = RequestContext.getCurrentContext().getRequestQueryParams();
        if (qp != null) return qp;

        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();

        qp = new LinkedHashMap<String, List<String>>();

        if (request.getQueryString() == null) return null;
        StringTokenizer st = new StringTokenizer(request.getQueryString(), "&");
        int i;

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            i = s.indexOf("=");
            if (i > 0 && s.length() >= i + 1) {
                String name = s.substring(0, i);
                String value = s.substring(i + 1);

                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (Exception e) {
                }
                try {
                    value = URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                }

                List<String> valueList = qp.get(name);
                if (valueList == null) {
                    valueList = new LinkedList<String>();
                    qp.put(name, valueList);
                }

                valueList.add(value);
            }
            else if (i == -1)
            {
                String name=s;
                String value="";
                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (Exception e) {
                }
               
                List<String> valueList = qp.get(name);
                if (valueList == null) {
                    valueList = new LinkedList<String>();
                    qp.put(name, valueList);
                }

                valueList.add(value);
                
            }
        }

        RequestContext.getCurrentContext().setRequestQueryParams(qp);
        return qp;
    }

    /**
     * Checks headers, query string, and form body for a given parameter
     *
     * @param sName
     * @return
     */
    public String getValueFromRequestElements(String sName) {
        String sValue = null;
        if (getQueryParams() != null) {
            final List<String> v = getQueryParams().get(sName);
            if (v != null && !v.isEmpty()) sValue = v.iterator().next();
        }
        if (sValue != null) return sValue;
        sValue = getHeaderValue(sName);
        if (sValue != null) return sValue;
        sValue = getFormValue(sName);
        if (sValue != null) return sValue;
        return null;
    }

    /**
     * return true if the client requested gzip content
     *
     * @param contentEncoding a <code>String</code> value
     * @return true if the content-encoding param containg gzip
     */
    public boolean isGzipped(String contentEncoding) {
        return contentEncoding.contains("gzip");
    }

    public static class UnitTest {

        @Mock
        private RequestContext mockContext;
        @Mock
        private HttpServletRequest request;

        private Map<String, List<String>> qp;

        private LinkedList<String> blankValue;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
            blankValue = new LinkedList<String>();
            blankValue.add("");

            RequestContext.testSetCurrentContext(mockContext);
            when(mockContext.getRequestQueryParams()).thenReturn(null);
            when(mockContext.getRequest()).thenReturn(request);
        }

        @Test
        public void detectsGzip() {
            assertTrue(HTTPRequestUtils.getInstance().isGzipped("gzip"));
        }

        @Test
        public void detectsNonGzip() {
            assertFalse(HTTPRequestUtils.getInstance().isGzipped("identity"));
        }

        @Test
        public void detectsGzipAmongOtherEncodings() {
            assertTrue(HTTPRequestUtils.getInstance().isGzipped("gzip, deflate"));
        }

        @Test
        public void testGetQueryParams() {
            when(request.getQueryString()).thenReturn("wsdl");
            
            qp = HTTPRequestUtils.getInstance().getQueryParams();
            assertEquals(blankValue, qp.get("wsdl"));
            
            when(request.getQueryString()).thenReturn("wsdl=");
            
            qp = HTTPRequestUtils.getInstance().getQueryParams();
            assertEquals(blankValue, qp.get("wsdl"));

            when(request.getQueryString()).thenReturn("a=123&b=234&b=345&c&d=");
            
            qp = HTTPRequestUtils.getInstance().getQueryParams();
            assertEquals("123", qp.get("a").get(0));
            assertEquals("234", qp.get("b").get(0)); 
            assertEquals("345", qp.get("b").get(1));
            assertEquals(blankValue, qp.get("c"));
            assertEquals(blankValue, qp.get("d"));
        }

        @Test
        public void testGetQueryParamsOrderIsPreserved() {
            when(request.getQueryString()).thenReturn("WSDL&interface=Foo&part=FooImpl.wsdl");

            qp = HTTPRequestUtils.getInstance().getQueryParams();
            assertEquals("WSDL", qp.keySet().iterator().next());
        }
    }

}
