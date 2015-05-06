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

import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
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

    private FilterUsageNotifier usageNotifier;


    public FilterProcessor() {
        usageNotifier = new BasicFilterUsageNotifier();
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
     * Override the default filter usage notification impl.
     *
     * @param notifier
     */
    public void setFilterUsageNotifier(FilterUsageNotifier notifier) {
        this.usageNotifier = notifier;
    }

    /**
     * runs "post" filters which are called after "route" filters. ZuulExceptions from ZuulFilters are thrown.
     * Any other Throwables are caught and a ZuulException is thrown out with a 500 status code
     *
     * @throws ZuulException
     */
    public SessionContext postRoute(SessionContext ctx) throws ZuulException {
        try {
            return runFilters(ctx, "post");
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
    public SessionContext error(SessionContext ctx) {
        try {
            return runFilters(ctx, "error");
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return ctx;
        }
    }

    /**
     * Runs all "route" filters. These filters route calls to an origin.
     *
     * @throws ZuulException if an exception occurs.
     */
    public SessionContext route(SessionContext ctx) throws ZuulException {
        try {
            return runFilters(ctx, "route");
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
    public SessionContext preRoute(SessionContext ctx) throws ZuulException {
        try {
            return runFilters(ctx, "pre");
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
    public SessionContext runFilters(SessionContext ctx, String sType) throws Throwable {
        if (ctx.getAttributes().debugRouting()) {
            Debug.addRoutingDebug(ctx, "Invoking {" + sType + "} type filters");
        }
        List<ZuulFilter> list = FilterLoader.getInstance().getFiltersByType(sType);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                ZuulFilter zuulFilter = list.get(i);

                // Apply the filter, and replace SessionContext with the context returned by the Filter.
                ctx = processZuulFilter(ctx, zuulFilter);
            }
        }
        return ctx;
    }

    /**
     * Processes an individual ZuulFilter. This method adds Debug information. Any uncaught Thowables are caught by this method and converted to a ZuulException with a 500 status code.
     *
     * @param filter
     * @return the return value for that filter
     * @throws ZuulException
     */
    public SessionContext processZuulFilter(SessionContext ctx, ZuulFilter filter) throws ZuulException {

        boolean bDebug = ctx.getAttributes().debugRouting();
        long execTime = 0;
        String filterName = "";
        try {
            long ltime = System.currentTimeMillis();
            filterName = filter.getClass().getSimpleName();
            
            SessionContext copy = null;
            SessionContext resultContext = null;
            Throwable t = null;

            if (bDebug) {
                Debug.addRoutingDebug(ctx, "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filterName);
                copy = (SessionContext) ctx.clone();
            }
            
            ZuulFilterResult result = filter.runFilter(ctx);
            ExecutionStatus s = result.getStatus();
            execTime = System.currentTimeMillis() - ltime;

            switch (s) {
                case FAILED:
                    t = result.getException();
                    ctx.getAttributes().addFilterExecutionSummary(filterName, ExecutionStatus.FAILED.name(), execTime);
                    break;
                case SUCCESS:
                    resultContext = result.getResult();
                    ctx.getAttributes().addFilterExecutionSummary(filterName, ExecutionStatus.SUCCESS.name(), execTime);
                    if (bDebug) {
                        Debug.addRoutingDebug(ctx, "Filter {" + filterName + " TYPE:" + filter.filterType() + " ORDER:" + filter.filterOrder() + "} Execution time = " + execTime + "ms");
                        Debug.compareContextState(filterName, ctx, copy);
                    }
                    break;
                default:
                    break;
            }
            
            if (t != null) throw t;

            usageNotifier.notify(filter, s);

            // If no resultContext returned from filter, then return the original context.
            return resultContext == null ? ctx : resultContext;

        } catch (Throwable e) {
            if (bDebug) {
                Debug.addRoutingDebug(ctx, "Running Filter failed " + filterName + " type:" + filter.filterType() + " order:" + filter.filterOrder() + " " + e.getMessage());
            }
            usageNotifier.notify(filter, ExecutionStatus.FAILED);
            if (e instanceof ZuulException) {
                throw (ZuulException) e;
            } else {
                ZuulException ex = new ZuulException(e, "Filter threw Exception", 500, filter.filterType() + ":" + filterName);
                ctx.getAttributes().addFilterExecutionSummary(filterName, ExecutionStatus.FAILED.name(), execTime);
                throw ex;
            }
        }
    }

    /**
     * Publishes a counter metric for each filter on each use.
     */
    public static class BasicFilterUsageNotifier implements FilterUsageNotifier {
        private static final String METRIC_PREFIX = "zuul.filter-";

        @Override
        public void notify(ZuulFilter filter, ExecutionStatus status) {
            DynamicCounter.increment(METRIC_PREFIX + filter.getClass().getSimpleName(), "status", status.name(), "filtertype", filter.filterType());
        }
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        ZuulFilter filter;

        @Mock
        HttpRequestMessage request;

        SessionContext ctx;
        HttpResponseMessage response;

        @Before
        public void before() {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);

            response = new HttpResponseMessage(200);
            ctx = new SessionContext(request, response);

            when(filter.filterType()).thenReturn("pre");
        }

        @Test
        public void testProcessZuulFilter() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            when(filter.runFilter(ctx)).thenReturn(new ZuulFilterResult());

            try {
                processor.processZuulFilter(ctx, filter);
                verify(processor, times(1)).processZuulFilter(ctx, filter);
                verify(filter, times(1)).runFilter(ctx);

            } catch (Throwable e) {
                e.printStackTrace();
                fail();
            }
        }

        @Test
        public void testProcessZuulFilterException() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                ZuulFilterResult r = new ZuulFilterResult(ExecutionStatus.FAILED);
                r.setException(new Exception("Test"));
                when(filter.runFilter(ctx)).thenReturn(r);
                when(filter.filterType()).thenReturn("post");
                processor.processZuulFilter(ctx, filter);
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
                processor.postRoute(ctx);
                verify(processor, times(1)).runFilters(ctx, "post");
            } catch (Throwable e) {
                e.printStackTrace();
                fail();
            }
        }

        @Test
        public void testPreProcess() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                processor.preRoute(ctx);
                verify(processor, times(1)).runFilters(ctx, "pre");
            } catch (Throwable e) {
                e.printStackTrace();
                fail();
            }
        }

        @Test
        public void testRouteProcess() {
            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                processor.route(ctx);
                verify(processor, times(1)).runFilters(ctx, "route");
            } catch (Throwable e) {
                e.printStackTrace();
                fail();
            }
        }

        @Test
        public void testRouteProcessHttpException() {

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters(ctx, "route")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.route(ctx);
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

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters(ctx, "route")).thenThrow(new Throwable("test"));
                when(filter.filterType()).thenReturn("post");
                processor.route(ctx);
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testPreProcessException() {

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters(ctx, "pre")).thenThrow(new Throwable("test"));
                when(filter.filterType()).thenReturn("post");
                processor.preRoute(ctx);
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testPreProcessHttpException() {

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters(ctx, "pre")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.preRoute(ctx);
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

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters(ctx, "post")).thenThrow(new Throwable("test"));
                when(filter.filterType()).thenReturn("post");
                processor.postRoute(ctx);
            } catch (ZuulException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testPostProcessHttpException() {

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters(ctx, "post")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.postRoute(ctx);
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
                when(processor.runFilters(ctx, "error")).thenThrow(new Exception("test"));
                when(filter.filterType()).thenReturn("post");
                processor.error(ctx);
                assertTrue(true);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testErrorHttpException() {

            FilterProcessor processor = new FilterProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters(ctx, "error")).thenThrow(new ZuulException("test", 400, "test"));
                when(filter.filterType()).thenReturn("post");
                processor.error(ctx);
                assertTrue(true);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }
    }
}
