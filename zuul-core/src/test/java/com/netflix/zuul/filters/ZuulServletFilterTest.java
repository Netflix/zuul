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

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class ZuulServletFilterTest {

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
            zuulServletFilter.setZuulRunner(zuulRunner);
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
            zuulServletFilter.setZuulRunner(zuulRunner);
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
            zuulServletFilter.setZuulRunner(zuulRunner);

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

            zuulServletFilter.setZuulRunner(zuulRunner);
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