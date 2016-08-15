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
package com.netflix.zuul.filters;


import com.netflix.zuul.ZuulRunner;
import com.netflix.zuul.context.RequestContext;
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
public class ZuulServletFilter implements Filter {

    private ZuulRunner zuulRunner;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        String bufferReqsStr = filterConfig.getInitParameter("buffer-requests");
        boolean bufferReqs = bufferReqsStr != null && bufferReqsStr.equals("true") ? true : false;

        zuulRunner = new ZuulRunner(bufferReqs);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
            try {
                preRouting();
            } catch (ZuulException e) {
                error(e);
                postRouting();
                return;
            }
            
            // Only forward onto to the chain if a zuul response is not being sent
            if (!RequestContext.getCurrentContext().sendZuulResponse()) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            
            try {
                routing();
            } catch (ZuulException e) {
                error(e);
                postRouting();
                return;
            }
            try {
                postRouting();
            } catch (ZuulException e) {
                error(e);
                return;
            }
        } catch (Throwable e) {
            error(new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_FROM_FILTER_" + e.getClass().getName()));
        } finally {
            RequestContext.getCurrentContext().unset();
        }
    }

    void postRouting() throws ZuulException {
        zuulRunner.postRoute();
    }

    void routing() throws ZuulException {
        zuulRunner.route();
    }

    void preRouting() throws ZuulException {
        zuulRunner.preRoute();
    }

    void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        zuulRunner.init(servletRequest, servletResponse);
    }

    void error(ZuulException e) {
        RequestContext.getCurrentContext().setThrowable(e);
        zuulRunner.error();
    }

    @Override
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


        @Before
        public void before() {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testRoutingException() {

            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);

            ZuulServletFilter zuulServletFilter = new ZuulServletFilter();
            zuulServletFilter = spy(zuulServletFilter);

            try {
                zuulServletFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulServletFilter).routing();

                zuulServletFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulServletFilter, times(1)).routing();
                verify(zuulServletFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }

        @Test
        public void testPreException() {

            ZuulServletFilter zuulServletFilter = new ZuulServletFilter();
            zuulServletFilter = spy(zuulServletFilter);
            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);


            try {
                zuulServletFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulServletFilter).preRouting();

                zuulServletFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulServletFilter, times(1)).preRouting();
                verify(zuulServletFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());

            }


        }


        @Test
        public void testPostException() {
            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);

            ZuulServletFilter zuulServletFilter = new ZuulServletFilter();
            zuulServletFilter = spy(zuulServletFilter);

            try {
                zuulServletFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulServletFilter).postRouting();

                zuulServletFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulServletFilter, times(1)).postRouting();
                verify(zuulServletFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }


        @Test
        public void testProcessZuulFilter() {

            ZuulServletFilter zuulServletFilter = new ZuulServletFilter();
            zuulServletFilter = spy(zuulServletFilter);

            try {

                zuulServletFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));
                zuulServletFilter.doFilter(servletRequest, servletResponse, filterChain);

                verify(zuulRunner, times(1)).init(servletRequest, servletResponse);
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
