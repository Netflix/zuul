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

import com.netflix.zuul.context.RequestContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Some handy methods for workign with HTTP requests
 * @author Mikey Cohen
 * Date: 2/10/12
 * Time: 8:22 AM
 */
public class HTTPRequestUtilsTest {
    @Mock
    private RequestContext mockContext;
    @Mock
    private HttpServletRequest request;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
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
        Map<String, List<String>> qp;
        LinkedList<String> blankValue = new LinkedList<String>();
        blankValue.add("");

        RequestContext.testSetCurrentContext(mockContext);
        when(mockContext.getRequestQueryParams()).thenReturn(null);
        when(mockContext.getRequest()).thenReturn(request);
        when(request.getQueryString()).thenReturn("wsdl");

        qp = HTTPRequestUtils.getInstance().getQueryParams();
        assertEquals(blankValue, qp.get("wsdl"));

        when(request.getQueryString()).thenReturn("wsdl=");

        qp = HTTPRequestUtils.getInstance().getQueryParams();
        assertEquals(blankValue, qp.get("wsdl"));

        when(request.getQueryString()).thenReturn("a=123&b=234&b=345&c&d=");

        qp = HTTPRequestUtils.getInstance().getQueryParams();
        assertEquals("123", qp.get("a").get(0));
        // Not sure that order is supposed to be guaranteed here
        assertEquals("234", qp.get("b").get(0));
        assertEquals("345", qp.get("b").get(1));
        assertEquals(blankValue, qp.get("c"));
        assertEquals(blankValue, qp.get("d"));
    }

}
