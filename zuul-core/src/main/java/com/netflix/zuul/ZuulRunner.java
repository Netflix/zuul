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
package com.netflix.zuul;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.http.HttpServletRequestWrapper;
import com.netflix.zuul.http.HttpServletResponseWrapper;


/**
 * This class initializes servlet requests and responses into the RequestContext and wraps the FilterProcessor calls
 * to preRoute(), route(),  postRoute(), and error() methods
 *
 * @author mikey@netflix.com
 * @version 1.0
 */
public class ZuulRunner {

    /**
     * Creates a new <code>ZuulRunner</code> instance.
     */
    public ZuulRunner() {
    }

    /**
     * sets HttpServlet request and HttpResponse
     *
     * @param servletRequest
     * @param servletResponse
     */
    public void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        RequestContext.getCurrentContext().setRequest(new HttpServletRequestWrapper(servletRequest));
        RequestContext.getCurrentContext().setResponse(new HttpServletResponseWrapper(servletResponse));
        //RequestContext.getCurrentContext().setResponseStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * executes "post" filterType  ZuulFilters
     *
     * @throws ZuulException
     */
    public void postRoute() throws ZuulException {
        FilterProcessor.getInstance().postRoute();
    }

    /**
     * executes "route" filterType  ZuulFilters
     *
     * @throws ZuulException
     */
    public void route() throws ZuulException {
        FilterProcessor.getInstance().route();
    }

    /**
     * executes "pre" filterType  ZuulFilters
     *
     * @throws ZuulException
     */
    public void preRoute() throws ZuulException {
        FilterProcessor.getInstance().preRoute();
    }

    /**
     * executes "error" filterType  ZuulFilters
     */
    public void error() {
        FilterProcessor.getInstance().error();
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        ZuulFilter filter;

        @Mock
        HttpServletRequest servletRequest;

        @Mock
        HttpServletResponse servletResponse;

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

            ZuulRunner runner = new ZuulRunner();
            runner = spy(runner);
            RequestContext context = spy(RequestContext.getCurrentContext());

            try {
                FilterProcessor.setProcessor(processor);
                RequestContext.testSetCurrentContext(context);
                when(servletResponse.getWriter()).thenReturn(writer);

                runner.init(servletRequest, servletResponse);
                verify(runner, times(1)).init(servletRequest, servletResponse);
                assertTrue(RequestContext.getCurrentContext().getRequest() instanceof HttpServletRequestWrapper);
                assertTrue(RequestContext.getCurrentContext().getResponse() instanceof HttpServletResponseWrapper);

                runner.preRoute();
                verify(processor, times(1)).preRoute();

                runner.postRoute();
                verify(processor, times(1)).postRoute();

                runner.route();
                verify(processor, times(1)).route();
                RequestContext.testSetCurrentContext(null);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}