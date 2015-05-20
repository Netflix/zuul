/*
 *
 *  Copyright 2013-2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.zuul;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.zuul.context.*;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.FilterError;
import com.netflix.zuul.filters.ShouldFilter;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * This the the core class to execute filters.
 *
 * @author Mikey Cohen
 * @author Mike Smith
 */
@Singleton
public class FilterProcessor {

    protected static final Logger LOG = LoggerFactory.getLogger(FilterProcessor.class);

    /** The name of the error filter to use if none specified in the context. */
    protected static final DynamicStringProperty DEFAULT_ERROR_ENDPOINT = DynamicPropertyFactory.getInstance()
            .getStringProperty("zuul.filters.error.default", "ErrorResponse");

    @Inject
    private FilterLoader filterLoader;

    private FilterUsageNotifier usageNotifier;


    public FilterProcessor() {
        usageNotifier = new BasicFilterUsageNotifier();
    }

    /**
     * Override the default filter usage notification impl.
     *
     * @param notifier
     */
    public void setFilterUsageNotifier(FilterUsageNotifier notifier) {
        this.usageNotifier = notifier;
    }


    public Observable<ZuulMessage> applyInboundFilters(Observable<ZuulMessage> chain)
    {
        chain = applyFilterPhase(chain, "in");

        chain = applyErrorFilterIfNeeded(chain);

        return chain;
    }

    public Observable<ZuulMessage> applyErrorFilterIfNeeded(Observable<ZuulMessage> chain)
    {
        return chain.flatMap(msg -> {

            // If flagged to, then apply the error endpoint filter.
            Attributes attributes = msg.getContext().getAttributes();
            if (attributes.shouldSendErrorResponse()) {

                // Get the error endpoint filter to use.
                String endpointName = (String) attributes.get("error-endpoint");
                if (endpointName == null) {
                    endpointName = DEFAULT_ERROR_ENDPOINT.get();
                }
                ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName, "end");
                if (endpointFilter == null) {
                    attributes.setShouldSendErrorResponse(true);
                    attributes.setThrowable(new ZuulException("No error filter found of chosen name! name=" + endpointName));
                    return Observable.just(msg);
                }

                // Un-flag this as needing the error filter.
                attributes.setShouldSendErrorResponse(false);

                // Apply this endpoint.
                return processAsyncFilter(msg, endpointFilter);
            }
            else {
                // Do nothing.
                return chain;
            }
        });
    }

    public Observable<ZuulMessage> applyEndpointFilter(Observable<ZuulMessage> chain)
    {
        chain = chain.flatMap(msg -> {

            // Get the previously chosen endpoint filter to use.
            Attributes attributes = msg.getContext().getAttributes();
            String endpointName = (String) msg.getAttributes().get("endpoint");
            if (endpointName == null) {
                attributes.setShouldSendErrorResponse(true);
                attributes.setThrowable(new ZuulException("No endpoint filter chosen!"));
                return Observable.just(msg);
            }
            ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName, "end");
            if (endpointFilter == null) {
                attributes.setShouldSendErrorResponse(true);
                attributes.setThrowable(new ZuulException("No endpoint filter found of chosen name! name=" + endpointName));
                return Observable.just(msg);
            }

            // Apply this endpoint.
            return processAsyncFilter(msg, endpointFilter);
        });

        // Apply the error filters AGAIN. This is for if there was an error during the endpoint phase.
        chain = applyErrorFilterIfNeeded(chain);

        return chain;
    }

    public Observable<ZuulMessage> applyOutboundFilters(Observable<ZuulMessage> chain)
    {
        // Apply POST filters.
        chain = applyFilterPhase(chain, "out");

        // And apply one last time to catch any errors from outbound filters.
        chain = applyErrorFilterIfNeeded(chain);

        return chain;
    }

    protected Observable<ZuulMessage> applyFilterPhase(Observable<ZuulMessage> chain, String filterType)
    {
        List<ZuulFilter> filters = filterLoader.getFiltersByType(filterType);
        for (ZuulFilter filter: filters) {
            chain = processFilterAsObservable(chain, filter).single();
        }
        return chain;
    }

    public Observable<ZuulMessage> processFilterAsObservable(Observable<ZuulMessage> input, ZuulFilter filter)
    {
        return input.flatMap(msg -> processAsyncFilter(msg, filter) );
    }

    /**
     * Processes an individual ZuulFilter. This method adds Debug information. Any uncaught Throwables from filters
     * are caught by this method and stored for future insight on the SessionContext.getFilterErrors().
     *
     * @param msg ZuulMessage
     * @param filter IZuulFilter
     * @return the return value for that filter
     */
    public Observable<ZuulMessage> processAsyncFilter(ZuulMessage msg, ZuulFilter filter)
    {
        final FilterExecInfo info = new FilterExecInfo();
        info.bDebug = msg.getContext().getAttributes().debugRouting();

        if (info.bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = msg.clone();
        }

        // Apply this filter.
        Observable<ZuulMessage> resultObs;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                resultObs = Observable.just(msg);
                info.status = ExecutionStatus.DISABLED;
            } else {
                // Only apply the filter if both the shouldFilter() method AND any additional
                // ShouldFilter impl pass.
                if (filter.shouldFilter(msg)) {
                    resultObs = filter.applyAsync(msg).single();
                } else {
                    resultObs = Observable.just(msg);
                    info.status = ExecutionStatus.SKIPPED;
                }
            }
        }
        catch (Exception e) {
            resultObs = Observable.just(msg);
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
        }

        // Handle errors from the filter. Don't break out of the filter chain - instead just record info about the error
        // in context, and continue.
        resultObs = resultObs.onErrorReturn((e) -> {
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
            return msg;
        });

        // If no resultContext returned from filter, then use the original context.
        resultObs = resultObs.map(msg2 -> msg2 == null ? msg : msg2);

        // Record info when filter processing completes.
        resultObs = resultObs.doOnCompleted(() -> {
            if (info.status == null) {
                info.status = ExecutionStatus.SUCCESS;
            }
            info.execTime = System.currentTimeMillis() - ltime;
            recordFilterCompletion(msg, filter, info);
        });

        return resultObs;
    }



    protected void recordFilterCompletion(ZuulMessage msg, ZuulFilter filter, FilterExecInfo info)
    {
        // Record the execution summary in context.
        switch (info.status) {
            case FAILED:
                msg.getContext().getAttributes().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.FAILED.name(), info.execTime);
                break;
            case SUCCESS:
                msg.getContext().getAttributes().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.SUCCESS.name(), info.execTime);
                if (info.bDebug) {
                    Debug.addRoutingDebug(msg.getContext(), "Filter {" + filter.filterName() + " TYPE:" + filter.filterType()
                            + " ORDER:" + filter.filterOrder() + "} Execution time = " + info.execTime + "ms");
                    Debug.compareContextState(filter.filterName(), msg.getContext(), info.debugCopy.getContext());
                }
                break;
            default:
                break;
        }

        // Notify configured listener.
        usageNotifier.notify(filter, info.status);
    }

    protected void recordFilterError(ZuulFilter filter, ZuulMessage msg, Throwable e)
    {
        // Add a log statement for this exception.
        String errorMsg = "Filter Exception: request-info=" + msg.getInfoForLogging() + ", msg=" + String.valueOf(e.getMessage());
        if (e instanceof ZuulException && ! ((ZuulException)e).shouldLogAsError()) {
            LOG.warn(errorMsg);
        } else {
            LOG.error(errorMsg, e);
        }

        // Store this filter error for possible future use. But we still continue with next filter in the chain.
        msg.getContext().getFilterErrors().add(new FilterError(filter.filterName(), filter.filterType(), e));

        boolean bDebug = msg.getContext().getAttributes().debugRouting();
        if (bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Running Filter failed " + filter.filterName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() + " " + e.getMessage());
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

    static class FilterExecInfo
    {
        ExecutionStatus status;
        boolean bDebug = false;
        long execTime;
        ZuulMessage debugCopy = null;
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        BaseSyncFilter filter;

        @Mock
        ShouldFilter additionalShouldFilter;

        SessionContext ctx;
        HttpRequestMessage request;
        HttpResponseMessage response;

        FilterProcessor processor;

        @Before
        public void before() {
            MonitoringHelper.initMocks();
            MockitoAnnotations.initMocks(this);

            ctx = new SessionContext();
            request = new HttpRequestMessage(ctx, "HTTP/1.1", "GET", "/somepath", new HttpQueryParams(), new Headers(), "127.0.0.1", "https");
            response = new HttpResponseMessage(ctx, request, 200);

            processor = new FilterProcessor();
            processor = spy(processor);

            when(filter.filterType()).thenReturn("pre");
            when(filter.shouldFilter(request)).thenReturn(true);
        }

        @Test
        public void testProcessFilter() throws Exception
        {
            when(filter.applyAsync(request)).thenReturn(Observable.just(request));
            processor.processAsyncFilter(request, filter).toBlocking().first();
            verify(filter, times(1)).applyAsync(request);
        }

        @Test
        public void testProcessFilter_ShouldFilterFalse() throws Exception
        {
            when(filter.shouldFilter(request)).thenReturn(false);
            processor.processAsyncFilter(request, filter).toBlocking().first();
            verify(filter, times(0)).applyAsync(request);
        }

        @Test
        public void testProcessFilterException()
        {
            Exception e = new RuntimeException("Blah");
            when(filter.applyAsync(request)).thenThrow(e);
            processor.processAsyncFilter(request, filter).toBlocking().first();

            verify(processor).recordFilterError(filter, request, e);

            ArgumentCaptor<FilterExecInfo> info = ArgumentCaptor.forClass(FilterExecInfo.class);
            verify(processor).recordFilterCompletion(same(request), same(filter), info.capture());
            assertEquals(ExecutionStatus.FAILED, info.getValue().status);
        }
//
//        @Test
//        public void testPostProcess() {
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                processor.postRoute(ctx);
//                verify(processor, times(1)).runFilters(ctx, "post");
//            } catch (Throwable e) {
//                e.printStackTrace();
//                fail();
//            }
//        }
//
//        @Test
//        public void testPreProcess() {
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                processor.preRoute(ctx);
//                verify(processor, times(1)).runFilters(ctx, "pre");
//            } catch (Throwable e) {
//                e.printStackTrace();
//                fail();
//            }
//        }
//
//        @Test
//        public void testRouteProcess() {
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                processor.route(ctx);
//                verify(processor, times(1)).runFilters(ctx, "route");
//            } catch (Throwable e) {
//                e.printStackTrace();
//                fail();
//            }
//        }
//
//        @Test
//        public void testRouteProcessHttpException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                when(processor.runFilters(ctx, "route")).thenThrow(new ZuulException("test", 400, "test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.route(ctx);
//            } catch (ZuulException e) {
//                assertEquals(e.getMessage(), "test");
//                assertEquals(e.nStatusCode, 400);
//            } catch (Throwable e) {
//                e.printStackTrace();
//                fail();
//
//            }
//
//        }
//
//        @Test
//        public void testRouteProcessException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//
//            try {
//                when(processor.runFilters(ctx, "route")).thenThrow(new Throwable("test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.route(ctx);
//            } catch (ZuulException e) {
//                assertEquals(e.getMessage(), "test");
//                assertEquals(e.nStatusCode, 500);
//            } catch (Throwable e) {
//                assertFalse(true);
//            }
//
//        }
//
//        @Test
//        public void testPreProcessException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//
//            try {
//                when(processor.runFilters(ctx, "pre")).thenThrow(new Throwable("test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.preRoute(ctx);
//            } catch (ZuulException e) {
//                assertEquals(e.getMessage(), "test");
//                assertEquals(e.nStatusCode, 500);
//            } catch (Throwable e) {
//                assertFalse(true);
//            }
//
//        }
//
//        @Test
//        public void testPreProcessHttpException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                when(processor.runFilters(ctx, "pre")).thenThrow(new ZuulException("test", 400, "test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.preRoute(ctx);
//            } catch (ZuulException e) {
//                assertEquals(e.getMessage(), "test");
//                assertEquals(e.nStatusCode, 400);
//            } catch (Throwable e) {
//                e.printStackTrace();
//                assertFalse(true);
//
//            }
//
//        }
//
//
//        @Test
//        public void testPostProcessException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//
//            try {
//                when(processor.runFilters(ctx, "post")).thenThrow(new Throwable("test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.postRoute(ctx);
//            } catch (ZuulException e) {
//                assertEquals(e.getMessage(), "test");
//                assertEquals(e.nStatusCode, 500);
//            } catch (Throwable e) {
//                assertFalse(true);
//            }
//
//        }
//
//        @Test
//        public void testPostProcessHttpException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                when(processor.runFilters(ctx, "post")).thenThrow(new ZuulException("test", 400, "test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.postRoute(ctx);
//            } catch (ZuulException e) {
//                assertEquals(e.getMessage(), "test");
//                assertEquals(e.nStatusCode, 400);
//            } catch (Throwable e) {
//                e.printStackTrace();
//                assertFalse(true);
//
//            }
//
//        }
//
//
//        @Test
//        public void testErrorException() {
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//
//            try {
//                when(processor.runFilters(ctx, "error")).thenThrow(new Exception("test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.error(ctx);
//                assertTrue(true);
//            } catch (Throwable e) {
//                assertFalse(true);
//            }
//
//        }
//
//        @Test
//        public void testErrorHttpException() {
//
//            FilterProcessor processor = new FilterProcessor();
//            processor = spy(processor);
//            try {
//                when(processor.runFilters(ctx, "error")).thenThrow(new ZuulException("test", 400, "test"));
//                when(filter.filterType()).thenReturn("post");
//                processor.error(ctx);
//                assertTrue(true);
//            } catch (Throwable e) {
//                e.printStackTrace();
//                assertFalse(true);
//
//            }
//
//        }
    }
}
