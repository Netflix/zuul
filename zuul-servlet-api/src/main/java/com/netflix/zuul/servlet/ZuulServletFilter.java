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
package com.netflix.zuul.servlet;


import com.netflix.zuul.ZuulRunner;
import com.netflix.zuul.context.Attributes;
import com.netflix.zuul.context.ServletSessionContextFactory;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * Zuul Servlet filter to run Zuul within a Servlet Filter. The filter invokes pre-routing filters first,
 * then routing filters, then post routing filters. Handled exceptions in pre-routing and routing
 * call the error filters, then call post-routing filters. Errors in post-routing only invoke the error filters.
 * Unhandled exceptions only invoke the error filters
 *
 * @author Mikey Cohen
 *         Date: 10/12/11
 *         Time: 2:54 PM
 */
public class ZuulServletFilter implements Filter
{
    private ServletSessionContextFactory contextFactory = new ServletSessionContextFactory();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        ZuulRunner runner = null;
        try {
            runner = init((HttpServletRequest) servletRequest);

            try {
                runner.preRoute();
            } catch (ZuulException e) {
                error(runner, e);
                runner.postRoute();
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
            try {
                runner.route();
            } catch (ZuulException e) {
                error(runner, e);
                runner.postRoute();
                return;
            }
            try {
                runner.postRoute();
            } catch (ZuulException e) {
                error(runner, e);
                return;
            }
        } catch (Throwable e) {
            if (runner != null) {
                error(runner, new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_FROM_FILTER_" + e.getClass().getName()));
            } else {
                throw new ServletException("Error initializing ZuulRunner for this request.", e);
            }
        }
    }

    ZuulRunner init(HttpServletRequest servletRequest)
    {
        SessionContext ctx = contextFactory.create(servletRequest);
        ZuulRunner zuulRunner = new ZuulRunner(ctx);
        return zuulRunner;
    }

    void error(ZuulRunner runner, ZuulException e) {
        runner.getContext().getAttributes().setThrowable(e);
        runner.error();
    }

    public void destroy() {

    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        HttpServletRequest servletRequest;

        @Mock
        HttpServletResponse servletResponse;

        @Mock
        FilterChain filterChain;
        @Mock
        ZuulRunner zuulRunner;
        @Mock
        ServletSessionContextFactory contextFactory;
        @Mock
        SessionContext context;

        Attributes attributes;

        ZuulServletFilter filter;


        @Before
        public void before() throws Exception
        {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);

            filter = new ZuulServletFilter();
            filter.contextFactory = contextFactory;
            filter = spy(filter);

            when(contextFactory.create(servletRequest)).thenReturn(context);
            doReturn(zuulRunner).when(filter).init(servletRequest);
            when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));
            when(zuulRunner.getContext()).thenReturn(context);

            attributes = new Attributes();
            when(context.getAttributes()).thenReturn(attributes);
        }

        @Test
        public void testRoutingException() {

            try {
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulRunner).route();

                filter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulRunner, times(1)).route();
                verify(filter, times(1)).error(zuulRunner, e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }
        }

        @Test
        public void testPreException() {

            try {
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulRunner).preRoute();

                filter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulRunner, times(1)).preRoute();
                verify(filter, times(1)).error(zuulRunner, e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }
        }


        @Test
        public void testPostException() {

            try {
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulRunner).postRoute();

                filter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulRunner, times(1)).postRoute();
                verify(filter, times(1)).error(zuulRunner, e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }
        }


        @Test
        public void testProcessZuulFilter() {

            try {
                filter.doFilter(servletRequest, servletResponse, filterChain);

                verify(zuulRunner, times(1)).preRoute();
                verify(zuulRunner, times(1)).route();
                verify(zuulRunner, times(1)).postRoute();

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }
        }
    }

}
