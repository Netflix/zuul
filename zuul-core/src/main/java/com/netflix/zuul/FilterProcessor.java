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

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.RequestContext;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * This the the core class to execute filters.
 *
 * @author Mikey Cohen
 *         Date: 10/24/11
 *         Time: 12:47 PM
 */
public class FilterProcessor {

    static FilterProcessor INSTANCE = new FilterProcessor();
    protected static final Logger logger = LoggerFactory.getLogger(FilterProcessor.class);


    public FilterProcessor() {
    }

    /**
     * @return the singleton FilterProcessor
     */
    public static FilterProcessor getInstance() {
        return INSTANCE;
    }

    /**
     * sets a singleton processor in case of a need to override default behavior
     *
     * @param processor
     */
    public static void setProcessor(FilterProcessor processor) {
        INSTANCE = processor;
    }

    /**
     * runs "post" filters which are called after "route" filters. ZuulExceptions from ZuulFilters are thrown.
     * Any other Throwables are caught and a ZuulException is thrown out with a 500 status code
     *
     * @throws ZuulException
     */
    public void postRoute() throws ZuulException {
        try {
            runFilters("post");
        } catch (Throwable e) {
            if (e instanceof ZuulException) {
                throw (ZuulException) e;
            }
            throw new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_IN_POST_FILTER_" + e.getClass().getName());
        }

    }

    /**
     * runs all "error" filters. These are called only if an exception occurs. Exceptions from this are swallowed and logged so as not to bubble up.
     */
    public void error() {
        try {
            runFilters("error");
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Runs all "route" filters. These filters route calls to an origin.
     *
     * @throws ZuulException if an exception occurs.
     */
    public void route() throws ZuulException {
        try {
            runFilters("route");
        } catch (Throwable e) {
            if (e instanceof ZuulException) {
                throw (ZuulException) e;
            }
            throw new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_IN_ROUTE_FILTER_" + e.getClass().getName());
        }
    }

    /**
     * runs all "pre" filters. These filters are run before routing to the orgin.
     *
     * @throws ZuulException
     */
    public void preRoute() throws ZuulException {
        try {
            runFilters("pre");
        } catch (Throwable e) {
            if (e instanceof ZuulException) {
                throw (ZuulException) e;
            }
            throw new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_IN_PRE_FILTER_" + e.getClass().getName());
        }
    }

    /**
     * runs all filters of the filterType sType/ Use this method within filters to run custom filters by type
     *
     * @param sType the filterType.
     * @return
     * @throws Throwable throws up an arbitrary exception
     */
    public Object runFilters(String sType) throws Throwable {
        if (RequestContext.getCurrentContext().debugRouting()) {
            Debug.addRoutingDebug("Invoking {" + sType + "} type filters");
        }
        boolean bResult = false;
        List<ZuulFilter> list = FilterLoader.getInstance().getFiltersByType(sType);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                ZuulFilter zuulFilter = list.get(i);
                Object result = processZuulFilter(zuulFilter);
                if (result != null && result instanceof Boolean) {
                    bResult |= ((Boolean) result);
                }
            }
        }
        return bResult;
    }

    /**
     * Processes an individual ZuulFilter. This method adds Debug information. Any uncaught Thowables are caught by this method and converted to a ZuulException with a 500 status code.
     *
     * @param filter
     * @return the return value for that filter
     * @throws ZuulException
     */
    public Object processZuulFilter(ZuulFilter filter) throws ZuulException {

        boolean bDebug = RequestContext.getCurrentContext().debugRouting();
        try {
            long ltime = System.currentTimeMillis();

            RequestContext copy = null;
            if (bDebug) {

                Debug.addRoutingDebug("Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.getClass().getSimpleName());
                copy = RequestContext.getCurrentContext().copy();
            }
            Object result = filter.runFilter();
            if (bDebug) {
                if (filter.shouldFilter()) {
                    Debug.addRoutingDebug("Filter {" + filter.getClass().getSimpleName() + " TYPE:" + filter.filterType() + " ORDER:" + filter.filterOrder() + "} Execution time = " + (System.currentTimeMillis() - ltime) + "ms");
                    Debug.compareContextState(filter.getClass().getSimpleName(), copy);
                } else {
                    //don't show filters not applied.
                }
            }
            return result;
        } catch (ZuulException e) {
            if (bDebug) {
                Debug.addRoutingDebug("Running Filter failed " + filter.getClass().getSimpleName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() +
                        " " + e.getMessage());
            }
            throw e;
        } catch (Throwable e) {
            if (bDebug) {
                Debug.addRoutingDebug("Running Filter failed " + filter.getClass().getSimpleName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() +
                        " " + e.getMessage());
            }
            ZuulException ex = new ZuulException(e, "Filter threw Exception", 500, filter.filterType() + ":" + filter.getClass().getSimpleName());
            throw ex;
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

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

            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Test
        public void testProcessZuulFilterException() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(filter.runFilter()).thenThrow(new Exception("Test"));
                processor.processZuulFilter(filter);
                assertFalse(true);
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
                processor.route();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

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
                processor.route();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
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
                processor.preRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
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
                processor.preRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

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
                processor.postRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
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
                processor.postRoute();
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }


        @Test
        public void testErrorException() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("error")).thenThrow(new Exception("test"));
                processor.error();
                assertTrue(true);
            } catch (Throwable e) {
                assertFalse(true);
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
                processor.error();
                assertTrue(true);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }


    }
}



