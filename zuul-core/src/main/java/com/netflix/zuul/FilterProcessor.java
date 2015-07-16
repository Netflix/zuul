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
import com.netflix.zuul.filters.*;
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
import rx.functions.Func1;

import javax.annotation.Nullable;
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
            .getStringProperty("zuul.filters.error.default", "endpoint.ErrorResponse");

    @Inject
    private FilterLoader filterLoader;

    @Inject
    @Nullable
    private FilterUsageNotifier usageNotifier;


    public FilterProcessor()
    {
        if (usageNotifier != null) {
            usageNotifier = new BasicFilterUsageNotifier();
        }
    }

    public Observable<ZuulMessage> applyInboundFilters(Observable<ZuulMessage> chain)
    {
        chain = applyFilterPhase(chain, "in", (request) -> request);

        chain = applyErrorEndpointIfNeeded(chain);

        return chain;
    }

    public Observable<ZuulMessage> applyErrorEndpointIfNeeded(Observable<ZuulMessage> chain)
    {
        return chain.flatMap(msg -> {

            // If flagged to, then apply the error endpoint filter.
            SessionContext context = msg.getContext();
            if (context.shouldSendErrorResponse()) {

                // Un-flag this as needing the error filter.
                context.setShouldSendErrorResponse(false);
                context.setErrorResponseSent(true);

                // Get the error endpoint filter to use.
                String endpointName = context.getErrorEndpoint();
                if (endpointName == null) {
                    endpointName = DEFAULT_ERROR_ENDPOINT.get();
                }
                ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName, "end");
                if (endpointFilter == null) {
                    // No error filter to use, so send a basic default response.
                    String errorStr = "No error filter found of chosen name! name=" + endpointName;
                    LOG.error("Errored but no error filter found, so sent default error response. " + errorStr, context.getError());

                    // Pull out the request object to use for building a new response.
                    HttpRequestMessage request;
                    if (HttpResponseMessageImpl.class.isAssignableFrom(msg.getClass())) {
                        request = ((HttpResponseMessage) msg).getRequest();
                    } else {
                        request = (HttpRequestMessage) msg;
                    }

                    // Build the error response.
                    HttpResponseMessage response = new HttpResponseMessageImpl(msg.getContext(), request, 500);
                    response.getHeaders().set("X-Zuul-Error-Cause", errorStr);

                    return Observable.just(response);
                }

                // Apply this endpoint.
                if (HttpResponseMessageImpl.class.isAssignableFrom(msg.getClass())) {
                    // if msg is a response, then we need to get it's request to pass to the error filter.
                    HttpResponseMessage response = (HttpResponseMessage) msg;
                    return processAsyncFilter(response.getRequest(), endpointFilter, (m2) -> m2, FilterPriority.LOW);
                }
                else {
                    return processAsyncFilter(msg, endpointFilter, (m2) -> m2, FilterPriority.LOW);
                }
            }
            else {
                // Do nothing.
                return Observable.just(msg);
            }
        });
    }

    /**
     * Applies the selected Endpoint filter.
     *
     * If the input message is a request, then the returned message should be a response.
     *
     * @param chain
     * @return
     */
    public Observable<ZuulMessage> applyEndpointFilter(Observable<ZuulMessage> chain)
    {
        chain = chain.flatMap(msg -> {

            SessionContext context = msg.getContext();
            HttpRequestMessage request = (HttpRequestMessage) msg;

            // If an error filter has already generated a response, then don't run the endpoint.
            if (context.errorResponseSent()) {
                // Therefore this msg is already a response, so just return that.
                return Observable.just(msg);
            }

            // Get the previously chosen endpoint filter to use.
            String endpointName = context.getEndpoint();
            if (endpointName == null) {
                context.setShouldSendErrorResponse(true);
                context.setError(new ZuulException("No endpoint filter chosen!"));
                return Observable.just(new HttpResponseMessageImpl(context, request, 500));
            }
            ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName, "end");
            if (endpointFilter == null) {
                context.setShouldSendErrorResponse(true);
                context.setError(new ZuulException("No endpoint filter found of chosen name! name=" + endpointName));
                return Observable.just(new HttpResponseMessageImpl(context, request, 500));
            }

            // Apply this endpoint. Make the default filter result be a HttpResponseMessage so that if this filter apply fails, the next filter still gets
            // expected input.
            return processAsyncFilter(msg, endpointFilter, (input) -> {
                // If the Endpoint filter does not run, or throws an exception, then
                // this should always require an error response to be sent.
                context.setShouldSendErrorResponse(true);
                return input;
            }, FilterPriority.LOW);
        });

        // Apply the error filters AGAIN. This is for if there was an error during the endpoint phase.
        chain = applyErrorEndpointIfNeeded(chain);

        return chain;
    }

    public Observable<ZuulMessage> applyOutboundFilters(Observable<ZuulMessage> chain)
    {
        // Apply POST filters.
        chain = applyFilterPhase(chain, "out", (response) -> response );

        // And apply one last time to catch any errors from outbound filters.
        chain = applyErrorEndpointIfNeeded(chain);

        return chain;
    }

    protected Observable<ZuulMessage> applyFilterPhase(Observable<ZuulMessage> chain, String filterType, Func1<ZuulMessage, ZuulMessage> defaultFilterResultChooser)
    {
        List<ZuulFilter> filters = filterLoader.getFiltersByType(filterType);
        for (ZuulFilter filter: filters) {
            chain = processFilterAsObservable(chain, filter, defaultFilterResultChooser).single();
        }
        return chain;
    }

    public Observable<ZuulMessage> processFilterAsObservable(Observable<ZuulMessage> input, ZuulFilter filter, Func1<ZuulMessage, ZuulMessage> defaultFilterResultChooser)
    {
        return input.flatMap(msg -> processAsyncFilter(msg, filter,
                defaultFilterResultChooser) );
    }

    /**
     * Processes an individual ZuulFilter. This method adds Debug information. Any uncaught Throwables from filters
     * are caught by this method and stored for future insight on the SessionContext.getFilterErrors().
     *
     * @param msg ZuulMessage
     * @param filter IZuulFilter
     * @return the return value for that filter
     */
    public Observable<ZuulMessage> processAsyncFilter(ZuulMessage msg, ZuulFilter filter,
                                                      Func1<ZuulMessage, ZuulMessage> defaultFilterResultChooser)
    {
        return processAsyncFilter(msg, filter, defaultFilterResultChooser, null);
    }

    public Observable<ZuulMessage> processAsyncFilter(ZuulMessage msg, ZuulFilter filter,
                                                      Func1<ZuulMessage, ZuulMessage> defaultFilterResultChooser,
                                                      FilterPriority overrideFilterPriority)
    {
        final FilterExecInfo info = new FilterExecInfo();
        info.bDebug = msg.getContext().debugRouting();

        if (info.bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = msg.clone();
        }

        // Apply this filter.
        Observable<ZuulMessage> resultObs;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                resultObs = Observable.just(defaultFilterResultChooser.call(msg));
                info.status = ExecutionStatus.DISABLED;
            } else {
                // Only apply the filter if both the shouldFilter() method AND the filter has a priority of
                // equal or above the requested.
                FilterPriority requiredPriority = overrideFilterPriority == null ? msg.getContext().getFilterPriorityToApply() : overrideFilterPriority;
                if (isFilterPriority(filter, requiredPriority) && filter.shouldFilter(msg)) {
                    resultObs = filter.applyAsync(msg).single();
                } else {
                    resultObs = Observable.just(defaultFilterResultChooser.call(msg));
                    info.status = ExecutionStatus.SKIPPED;
                }
            }
        }
        catch (Exception e) {
            msg.getContext().setError(e);
            resultObs = Observable.just(defaultFilterResultChooser.call(msg));
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
        }

        // Handle errors from the filter. Don't break out of the filter chain - instead just record info about the error
        // in context, and continue.
        resultObs = resultObs.onErrorReturn((e) -> {
            msg.getContext().setError(e);
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
            return defaultFilterResultChooser.call(msg);
        });

        // If no resultContext returned from filter, then use the original context.
        resultObs = resultObs.map(msg2 -> msg2 == null ? defaultFilterResultChooser.call(msg) : msg2);

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

    private boolean isFilterPriority(ZuulFilter filter, FilterPriority requiredPriority)
    {
        return filter.getPriority().getCode() >= requiredPriority.getCode();
    }

    protected void recordFilterCompletion(ZuulMessage msg, ZuulFilter filter, FilterExecInfo info)
    {
        // Record the execution summary in context.
        switch (info.status) {
            case FAILED:
                msg.getContext().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.FAILED.name(), info.execTime);
                break;
            case SUCCESS:
                msg.getContext().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.SUCCESS.name(), info.execTime);
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

    /**
     * Log the throwable, and also store info about it in the SessionContext for future reference.
     *
     * @param filter
     * @param msg
     * @param e
     */
    protected void recordFilterError(ZuulFilter filter, ZuulMessage msg, Throwable e)
    {
        // Add a log statement for this exception.
        String errorMsg = "Filter Exception: filter=" + String.valueOf(filter) + ", request-info=" + msg.getInfoForLogging() + ", msg=" + String.valueOf(e.getMessage());
        if (e instanceof ZuulException && ! ((ZuulException)e).shouldLogAsError()) {
            LOG.warn(errorMsg);
        } else {
            LOG.error(errorMsg, e);
        }

        // Store this filter error for possible future use. But we still continue with next filter in the chain.
        msg.getContext().getFilterErrors().add(new FilterError(filter.filterName(), filter.filterType(), e));

        boolean bDebug = msg.getContext().debugRouting();
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
            request = new HttpRequestMessageImpl(ctx, "HTTP/1.1", "GET", "/somepath", new HttpQueryParams(),
                    new Headers(), "127.0.0.1", "https", 80, "localhost");
            response = new HttpResponseMessageImpl(ctx, request, 200);

            processor = new FilterProcessor();
            processor = spy(processor);

            when(filter.filterType()).thenReturn("pre");
            when(filter.shouldFilter(request)).thenReturn(true);
            when(filter.getPriority()).thenReturn(FilterPriority.NORMAL);
        }

        @Test
        public void testProcessFilter() throws Exception {
            when(filter.applyAsync(request)).thenReturn(Observable.just(request));
            processor.processAsyncFilter(request, filter, (m) -> m).toBlocking().first();
            verify(filter, times(1)).applyAsync(request);
        }

        @Test
        public void testProcessFilter_ShouldFilterFalse() throws Exception
        {
            when(filter.shouldFilter(request)).thenReturn(false);
            processor.processAsyncFilter(request, filter, (m) -> m).toBlocking().first();
            verify(filter, times(0)).applyAsync(request);
        }

        @Test
        public void testProcessFilterException()
        {
            Exception e = new RuntimeException("Blah");
            when(filter.applyAsync(request)).thenThrow(e);
            processor.processAsyncFilter(request, filter, (m) -> m).toBlocking().first();

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
