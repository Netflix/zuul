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
package com.netflix.zuul.http;

import com.netflix.zuul.FilterProcessor;
import com.netflix.zuul.context.RequestContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ZuulServletTest {

    @Mock
    HttpServletRequest servletRequest;
    @Mock
    HttpServletResponseWrapper servletResponse;
    @Mock
    FilterProcessor processor;
    @Mock
    PrintWriter writer;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcessZuulFilter() {

        ZuulServlet zuulServlet = new ZuulServlet();
        zuulServlet = spy(zuulServlet);

        RequestContext context = spy(RequestContext.getCurrentContext());

        try {
            FilterProcessor.setProcessor(processor);
            RequestContext.testSetCurrentContext(context);
            when(servletResponse.getWriter()).thenReturn(writer);

            zuulServlet.init(servletRequest, servletResponse);
            verify(zuulServlet, times(1)).init(servletRequest, servletResponse);
            assertTrue(RequestContext.getCurrentContext().getRequest() instanceof HttpServletRequestWrapper);
            assertTrue(RequestContext.getCurrentContext().getResponse() instanceof HttpServletResponseWrapper);

            zuulServlet.preRoute();
            verify(processor, times(1)).preRoute();

            zuulServlet.postRoute();
            verify(processor, times(1)).postRoute();
//                verify(com.netflix.zuul.context, times(1)).unset();

            zuulServlet.route();
            verify(processor, times(1)).route();
            RequestContext.testSetCurrentContext(null);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
