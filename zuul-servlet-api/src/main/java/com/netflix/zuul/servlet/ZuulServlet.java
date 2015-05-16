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

import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.FilterProcessor;
import com.netflix.zuul.context.*;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
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
@Singleton
public class ZuulServlet extends HttpServlet {
    
    private static final long serialVersionUID = -3374242278843351501L;
    private static Logger LOG = LoggerFactory.getLogger(ZuulServlet.class);

    @Inject
    private FilterProcessor processor;

    @Inject
    private FilterFileManager filterManager;

    @javax.inject.Inject
    private ServletSessionContextFactory contextFactory;

    @Inject @Nullable
    private SessionContextDecorator decorator;


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    public void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException
    {
        try {
            // Setup the context for this request.
            SessionContext context = new SessionContext();
            // Optionally decorate the context.
            if (decorator != null) {
                context = decorator.decorate(context);
            }

            // Build a ZuulMessage from the servlet request.
            Observable<ZuulMessage> chain = contextFactory.create(context, servletRequest);

            // Apply the filters to the chain.
            chain = processor.applyInboundFilters(chain);
            chain = processor.applyEndpointFilter(chain);
            chain = processor.applyOutboundFilters(chain);

            // Execute and convert to blocking to get the resulting SessionContext.
            HttpResponseMessage response = (HttpResponseMessage) chain.single().toBlocking().first();

            // Write out the built response message to the HttpServletResponse.
            try {
                contextFactory.write(response, servletResponse);
            }
            catch (Exception e) {
                LOG.error("Error writing response message!", e);
                throw new ServletException("Error writing response message!", e);
            }
        }
        catch (Throwable e) {
            throw new ServletException("Error initializing ZuulRunner for this request.", e);
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        HttpServletRequest servletRequest;

        @Mock
        HttpServletResponse servletResponse;

        @Mock
        FilterProcessor processor;
        @Mock
        ServletSessionContextFactory contextFactory;
        @Mock
        SessionContext context;

        @Mock
        HttpRequestMessage request;

        Attributes attributes;
        HttpResponseMessage response;

        ZuulServlet servlet;


        @Before
        public void before() throws Exception {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);

            when(servletRequest.getMethod()).thenReturn("get");

            servlet = new ZuulServlet();
            servlet.contextFactory = contextFactory;
            servlet = spy(servlet);
            when(servletResponse.getWriter()).thenReturn(new PrintWriter(new ByteArrayOutputStream()));

            when(contextFactory.create(context, servletRequest)).thenReturn(Observable.just(request));

            attributes = new Attributes();
            response = new HttpResponseMessage(context, request, 299);
            when(context.getAttributes()).thenReturn(attributes);

            when(processor.applyOutboundFilters(Matchers.any())).thenReturn(Observable.just(response));
        }

        @Test
        public void testService() throws Exception
        {
            servlet.service(servletRequest, servletResponse);

            verify(servletResponse).setStatus(299);
        }
    }

}
