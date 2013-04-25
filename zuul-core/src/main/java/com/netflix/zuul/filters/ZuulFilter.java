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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mikey Cohen
 * Date: 10/12/11
 * Time: 2:54 PM
 */
public class ZuulFilter implements Filter {


    private ZuulRunner zuulRunner = new ZuulRunner();

    public void init(FilterConfig filterConfig) throws ServletException {

    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
            try {
                preProxy();
            } catch (ZuulException e) {
                error(e);
                postProxy();
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
            try {
                proxy();
            } catch (ZuulException e) {
                error(e);
                postProxy();
                return;
            }
            try {
                postProxy();
            } catch (ZuulException e) {
                error(e);
                return;
            }
        } catch (Throwable e) {
            error(new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_FROM_FILTER_" +e.getClass().getName()));
        } finally {
            RequestContext.getCurrentContext().unset();
        }
    }

    void postProxy() throws ZuulException {
        zuulRunner.postRoute();
    }

    void proxy() throws ZuulException {
        zuulRunner.route();
    }

    void preProxy() throws ZuulException {
        zuulRunner.preRoute();
    }

    void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        zuulRunner.init(servletRequest, servletResponse);
    }

    void error(ZuulException e) {
        RequestContext.getCurrentContext().setThrowable(e);
        zuulRunner.error();
        e.printStackTrace();
    }

    public void destroy() {
        //To change body of implemented methods use File | Settings | File Templates.
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
            MonitoringHelper.mockForTests();
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testProxyException() {

            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);

            ZuulFilter zuulFilter = new ZuulFilter();
            zuulFilter = spy(zuulFilter);

            try {
                zuulFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulFilter).proxy();

                zuulFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulFilter, times(1)).proxy();
                verify(zuulFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }

        @Test
        public void testPreProxyException() {

            ZuulFilter zuulFilter = new ZuulFilter();
            zuulFilter = spy(zuulFilter);
            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);


            try {
                zuulFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulFilter).preProxy();

                zuulFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulFilter, times(1)).preProxy();
                verify(zuulFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());

            }


        }


        @Test
        public void testPostProxyException() {
            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);

            ZuulFilter zuulFilter = new ZuulFilter();
            zuulFilter = spy(zuulFilter);

            try {
                zuulFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                ZuulException e = new ZuulException("test", 510, "test");
                doThrow(e).when(zuulFilter).postProxy();

                zuulFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(zuulFilter, times(1)).postProxy();
                verify(zuulFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }


        @Test
        public void testProcessProxyFilter() {

            ZuulFilter zuulFilter = new ZuulFilter();
            zuulFilter = spy(zuulFilter);

            try {

                zuulFilter.zuulRunner = zuulRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                zuulFilter.doFilter(servletRequest, servletResponse, filterChain);

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
