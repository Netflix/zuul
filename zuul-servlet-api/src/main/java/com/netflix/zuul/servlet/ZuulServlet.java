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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import static org.mockito.Mockito.*;

/**
 * Core Zuul servlet which intializes and orchestrates zuulFilter execution
 *
 * @author Mikey Cohen
 *         Date: 12/23/11
 *         Time: 10:44 AM
 */
public class ZuulServlet extends HttpServlet {
    
    private static final long serialVersionUID = -3374242278843351501L;
    private static Logger LOG = LoggerFactory.getLogger(ZuulServlet.class);

    private ServletSessionContextFactory contextFactory = new ServletSessionContextFactory();


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    public void service(javax.servlet.ServletRequest servletRequest, javax.servlet.ServletResponse servletResponse) throws ServletException, IOException
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

        }
        catch (Throwable e) {
            if (runner != null) {
                error(runner, new ZuulException(e, 500, "UNHANDLED_EXCEPTION_" + e.getClass().getName()));
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

    /**
     * sets error context info and executes "error" filters
     *
     * @param e
     */
    void error(ZuulRunner runner, ZuulException e) {
        runner.getContext().getAttributes().setThrowable(e);
        runner.error();
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

        ZuulServlet servlet;


        @Before
        public void before() throws Exception {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);

            servlet = new ZuulServlet();
            servlet.contextFactory = contextFactory;
            servlet = spy(servlet);
            doReturn(zuulRunner).when(servlet).init(servletRequest);
            when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));

            when(contextFactory.create(servletRequest)).thenReturn(context);
            when(zuulRunner.getContext()).thenReturn(context);

            attributes = new Attributes();
            when(context.getAttributes()).thenReturn(attributes);
        }

        @Test
        public void testProcessZuulFilter() {

            try {
                servlet.service(servletRequest, servletResponse);
                verify(zuulRunner, times(1)).preRoute();
                verify(zuulRunner, times(1)).route();
                verify(zuulRunner, times(1)).postRoute();

            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }


        }
    }

}
