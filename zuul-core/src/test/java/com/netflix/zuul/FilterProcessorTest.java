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


import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FilterProcessorTest {

        @Mock
        ZuulFilter filter;

        @Before
        public void before() {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testProcessZuulFilter() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                processor.processZuulFilter(filter);
                verify(processor, times(1)).processZuulFilter(filter);
                verify(filter, times(1)).runFilter();

            } catch (Throwable ignored) {
            }
        }

        @Test
        public void testProcessZuulFilterException() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                ZuulFilterResult r = new ZuulFilterResult(ExecutionStatus.FAILED);
                r.setException(new Exception("Test"));
                when(filter.runFilter()).thenReturn(r);
                when(filter.filterType()).thenReturn("post");
                processor.processZuulFilter(filter);
                fail();
            } catch (Throwable e) {
                assertEquals(e.getCause().getMessage(), "Test");
            }
        }


        @Test
        public void testPostProcess() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                processor.postRoute();
                verify(processor, times(1)).runFilters("post");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Test
        public void testPreProcess() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                processor.preRoute();
                verify(processor, times(1)).runFilters("pre");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Test
        public void testRouteProcess() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                processor.route();
                verify(processor, times(1)).runFilters("route");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Test
        public void testRouteProcessHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("route")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.route();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                fail();

            }

        }

        @Test
        public void testRouteProcessException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("route")).thenThrow(new Throwable("test"));
                when(filter.filterType()).thenReturn("post");
                processor.route();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                fail();
            }

        }

        @Test
        public void testPreProcessException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("pre")).thenThrow(new Throwable("test"));
                when(filter.filterType()).thenReturn("post");
                processor.preRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                fail();
            }

        }

        @Test
        public void testPreProcessHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("pre")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.preRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                fail();
            }

        }


        @Test
        public void testPostProcessException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("post")).thenThrow(new Throwable("test"));
                when(filter.filterType()).thenReturn("post");
                processor.postRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                fail();
            }

        }

        @Test
        public void testPostProcessHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("post")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.postRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {

                e.printStackTrace();
                fail();
            }

        }


        @Test
        public void testErrorException() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("error")).thenThrow(new Exception("test"));
                when(filter.filterType()).thenReturn("post");
                processor.error();
                assertTrue(true);
            } catch (Throwable e) {
                fail();
            }

        }

        @Test
        public void testErrorHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("error")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.error();
                assertTrue(true);
            } catch (Throwable e) {
                e.printStackTrace();
                fail();

            }

        }
}
