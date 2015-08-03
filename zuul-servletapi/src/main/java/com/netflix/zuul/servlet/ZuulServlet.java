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
import com.netflix.zuul.ZuulHttpProcessor;
import com.netflix.zuul.context.ServletSessionContextFactory;
import com.netflix.zuul.context.SessionCleaner;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

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

    private ZuulHttpProcessor zuulProcessor;

    @Inject
    public ZuulServlet(ZuulHttpProcessor zuulProcessor)
    {
        super();
        this.zuulProcessor = zuulProcessor;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    public void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException
    {
        try {
            zuulProcessor
                    .process(servletRequest, servletResponse)
                    .doOnNext(msg -> {
                        // Store this response as an attribute for any later ServletFilters that may want access to info in it.
                        servletRequest.setAttribute("_zuul_response", msg);
                    })
                    .subscribe();
        }
        catch (Throwable e) {
            LOG.error("Unexpected error running ZuulHttpProcessor for this request.", e);
            throw new ServletException("Unexpected error running ZuulHttpProcessor for this request.");
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
        @Mock
        HttpServletRequest servletRequest;
        @Mock
        HttpServletResponse servletResponse;
        @Mock
        ServletOutputStream servletOutputStream;
        @Mock
        FilterProcessor processor;
        @Mock
        FilterFileManager filterFileMgr;
        @Mock
        SessionCleaner sessionCleaner;

        @Mock
        HttpRequestMessage request;

        SessionContext context;
        HttpResponseMessage response;

        ZuulServlet servlet;
        ZuulHttpProcessor zuulProcessor;
        ServletInputStreamWrapper servletInputStream;
        ServletSessionContextFactory contextFactory;


        @Before
        public void before() throws Exception {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);

            contextFactory = new ServletSessionContextFactory();
            zuulProcessor = new ZuulHttpProcessor(processor, contextFactory, null, null, filterFileMgr, sessionCleaner);
            servlet = new ZuulServlet(zuulProcessor);
            servlet = spy(servlet);

            when(servletRequest.getHeaderNames()).thenReturn(Collections.<String>emptyEnumeration());
            when(servletRequest.getAttributeNames()).thenReturn(Collections.<String>emptyEnumeration());
            servletInputStream = new ServletInputStreamWrapper("{}".getBytes());
            when(servletRequest.getInputStream()).thenReturn(servletInputStream);
            when(servletResponse.getOutputStream()).thenReturn(servletOutputStream);

            //when(contextFactory.create(context, servletRequest)).thenReturn(Observable.just(request));

            response = new HttpResponseMessageImpl(context, request, 299);
            response.setBody("blah".getBytes());

            when(processor.applyInboundFilters(Matchers.any())).thenReturn(Observable.just(request));
            when(processor.applyEndpointFilter(Matchers.any())).thenReturn(Observable.just(response));
            when(processor.applyOutboundFilters(Matchers.any())).thenReturn(Observable.just(response));
        }

        @Test
        public void testService() throws Exception
        {
            when(servletRequest.getMethod()).thenReturn("get");
            when(servletRequest.getRequestURI()).thenReturn("/some/where?k1=v1");
            response.getHeaders().set("new", "value");

            servlet.service(servletRequest, servletResponse);

            verify(servletResponse).setStatus(299);
            verify(servletResponse).addHeader("new", "value");
        }
    }


    /**
     * Only for unit-testing purposes.
     */
    static class ServletInputStreamWrapper extends ServletInputStream
    {
        private byte[] data;
        private int idx = 0;

        public ServletInputStreamWrapper(byte[] data) {
            if(data == null) {
                data = new byte[0];
            }

            this.data = data;
        }

        public int read() throws IOException {
            return this.idx == this.data.length?-1:this.data[this.idx++] & 255;
        }
    }
}
