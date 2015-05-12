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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * This the the core class to execute filters.
 *
 * @author Mikey Cohen
 *         Date: 10/24/11
 *         Time: 12:47 PM
 */
@Singleton
public class FilterProcessor {

    protected static final Logger LOG = LoggerFactory.getLogger(FilterProcessor.class);

    protected static final DynamicStringProperty DEFAULT_FILTER_SYNC_TYPE = DynamicPropertyFactory.getInstance()
            .getStringProperty("zuul.filters.synctype.default", "sync");

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

    /**
     * Apply all the phases of configured filters to this context.
     *
     * @param chain
     * @return
     */
    public Observable<SessionContext> applyAllFilters(Observable<SessionContext> chain) {

        // Apply PRE filters.
        chain = applyFilterPhase(chain, "pre", null);

        // Apply the error filters. They will only pass shouldFilter() if the context's errorFlag has been set.
        chain = applyFilterPhase(chain, "error", ErrorShouldFilter.INSTANCE);

        // Route/Proxy.
        chain = applyFilterPhase(chain, "route", RouteShouldFilter.INSTANCE);

        // Apply the error filters AGAIN. This is for if there was an error during the route/proxy phase.
        chain = applyFilterPhase(chain, "error", ErrorShouldFilter.INSTANCE);

        // Apply POST filters.
        chain = applyFilterPhase(chain, "post", null);

        return chain;
    }

    protected Observable<SessionContext> applyFilterPhase(Observable<SessionContext> chain, String filterType, ShouldFilter additionalShouldFilter)
    {
        List<ZuulFilter> filters = filterLoader.getFiltersByType(filterType);
        for (ZuulFilter filter: filters) {
            chain = processFilterAsObservable(chain, filter, additionalShouldFilter).single();
        }
        return chain;
    }

    public Observable<SessionContext> processFilterAsObservable(Observable<SessionContext> input, ZuulFilter filter, ShouldFilter additionalShouldFilter)
    {
        // If this filter can be used Async OR Sync, then choose based on the configured default.
        // This default will likely be Sync for Servlet-based servers, and Async for netty-based.
        if (filter instanceof AsyncFilter && filter instanceof SyncFilter) {
            return processFilterWithSyncType(input, filter, additionalShouldFilter, DEFAULT_FILTER_SYNC_TYPE.get());
        }
        else if (filter instanceof AsyncFilter) {
            return processFilterWithSyncType(input, filter, additionalShouldFilter, "async");
        }
        else {
            return processFilterWithSyncType(input, filter, additionalShouldFilter, "sync");
        }
    }

    protected Observable<SessionContext> processFilterWithSyncType(Observable<SessionContext> input, ZuulFilter filter, ShouldFilter additionalShouldFilter, String syncType)
    {
        if ("async".equals(syncType)) {
            AsyncFilter asyncFilter = (AsyncFilter) filter;
            return input.flatMap(ctx -> processAsyncFilter(ctx, asyncFilter, additionalShouldFilter) );
        }
        else {
            SyncFilter syncFilter = (SyncFilter) filter;
            return input.map(ctx -> processFilter(ctx, syncFilter, additionalShouldFilter));
        }
    }

    public Observable<SessionContext> processAsyncFilter(SessionContext ctx, AsyncFilter filter, ShouldFilter additionalShouldFilter)
    {
        FilterExecInfo info = new FilterExecInfo();
        info.bDebug = ctx.getAttributes().debugRouting();

        if (info.bDebug) {
            Debug.addRoutingDebug(ctx, "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = (SessionContext) ctx.clone();
        }

        // Apply this filter.
        Observable<SessionContext> resultObs;
        long ltime = System.currentTimeMillis();
        if (filter.isDisabled()) {
            resultObs = Observable.just(ctx);
            info.status = ExecutionStatus.DISABLED;
        }
        else {
            // Only apply the filter if both the shouldFilter() method AND any additional
            // ShouldFilter impl pass.
            if (filter.shouldFilter(ctx)
                    && (additionalShouldFilter == null || additionalShouldFilter.shouldFilter(ctx))) {
                resultObs = filter.applyAsync(ctx);
                info.status = ExecutionStatus.SUCCESS;
            }
            else {
                resultObs = Observable.just(ctx);
                info.status = ExecutionStatus.SKIPPED;
            }
        }

        // Handle errors from the filter. Don't break out of the filter chain - instead just record info about the error
        // in context, and continue.
        resultObs = resultObs.onErrorReturn((e) -> {
            recordFilterError(filter, ctx, e);
            return ctx;
        });

        // If no resultContext returned from filter, then use the original context.
        resultObs = resultObs.map(ctx2 -> ctx2 == null ? ctx : ctx2);

        // Record info when filter processing completes.
        resultObs = resultObs.doOnCompleted(() -> {
            info.execTime = System.currentTimeMillis() - ltime;
            recordFilterCompletion(ctx, filter, info);
        });

        return resultObs;
    }

    /**
     * Processes an individual ZuulFilter. This method adds Debug information. Any uncaught Throwables from filters
     * are caught by this method and stored for future insight on the SessionContext.getFilterErrors().
     *
     * @param ctx SessionContext
     * @param filter IZuulFilter
     * @return the return value for that filter
     */
    public SessionContext processFilter(SessionContext ctx, SyncFilter filter, ShouldFilter additionalShouldFilter)
    {
        FilterExecInfo info = new FilterExecInfo();
        info.bDebug = ctx.getAttributes().debugRouting();

        if (info.bDebug) {
            Debug.addRoutingDebug(ctx, "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = (SessionContext) ctx.clone();
        }

        // Execute the filter.
        SessionContext resultContext = null;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                resultContext = ctx;
                info.status = ExecutionStatus.DISABLED;
            }
            else {
                // Only apply the filter if both the shouldFilter() method AND any additional
                // ShouldFilter impl pass.
                if (filter.shouldFilter(ctx)
                        && (additionalShouldFilter == null || additionalShouldFilter.shouldFilter(ctx)))
                {
                    resultContext = filter.apply(ctx);
                    info.status = ExecutionStatus.SUCCESS;
                }
                else {
                    resultContext = ctx;
                    info.status = ExecutionStatus.SKIPPED;
                }
            }
        }
        catch (Exception e) {
            resultContext = ctx;
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, ctx, e);
        }
        finally {
            info.execTime = System.currentTimeMillis() - ltime;
        }

        // If no resultContext returned from filter, then use the original context.
        if (resultContext != null) {
            ctx = resultContext;
        }

        recordFilterCompletion(ctx, filter, info);

        return ctx;
    }

    protected void recordFilterCompletion(SessionContext ctx, ZuulFilter filter, FilterExecInfo info)
    {
        // Record the execution summary in context.
        switch (info.status) {
            case FAILED:
                ctx.getAttributes().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.FAILED.name(), info.execTime);
                break;
            case SUCCESS:
                ctx.getAttributes().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.SUCCESS.name(), info.execTime);
                if (info.bDebug) {
                    Debug.addRoutingDebug(ctx, "Filter {" + filter.filterName() + " TYPE:" + filter.filterType()
                            + " ORDER:" + filter.filterOrder() + "} Execution time = " + info.execTime + "ms");
                    Debug.compareContextState(filter.filterName(), ctx, info.debugCopy);
                }
                break;
            default:
                break;
        }

        // Notify configured listener.
        usageNotifier.notify(filter, info.status);
    }

    protected void recordFilterError(ZuulFilter filter, SessionContext ctx, Throwable e)
    {
        // Add a log statement for this exception.
        String msg = "Filter Exception: request-info=" + ctx.getRequestInfoForLogging() + ", msg=" + String.valueOf(e.getMessage());
        if (e instanceof ZuulException && ! ((ZuulException)e).shouldLogAsError()) {
            LOG.warn(msg);
        } else {
            LOG.error(msg, e);
        }

        // Store this filter error for possible future use. But we still continue with next filter in the chain.
        ctx.getFilterErrors().add(new FilterError(filter.filterName(), filter.filterType(), e));

        boolean bDebug = ctx.getAttributes().debugRouting();
        if (bDebug) {
            Debug.addRoutingDebug(ctx, "Running Filter failed " + filter.filterName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() + " " + e.getMessage());
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
        SessionContext debugCopy = null;
    }

    public static class RouteShouldFilter implements ShouldFilter
    {
        public static final RouteShouldFilter INSTANCE = new RouteShouldFilter();

        @Override
        public boolean shouldFilter(SessionContext ctx) {
            return ctx.getAttributes().shouldProxy()
                    && ! ctx.getAttributes().shouldSendErrorResponse();
        }
    }


    public static class ErrorShouldFilter implements ShouldFilter
    {
        public static final ErrorShouldFilter INSTANCE = new ErrorShouldFilter();

        @Override
        public boolean shouldFilter(SessionContext ctx) {
            return ctx.getAttributes().shouldSendErrorResponse();
        }
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

            request = new HttpRequestMessage("HTTP/1.1", "GET", "/somepath", new HttpQueryParams(), new Headers(), "127.0.0.1", "https");
            response = new HttpResponseMessage(200);
            ctx = new SessionContext(request, response);

            processor = new FilterProcessor();
            processor = spy(processor);

            when(filter.filterType()).thenReturn("pre");
            when(filter.shouldFilter(ctx)).thenReturn(true);
        }

        @Test
        public void testProcessFilter() throws Exception
        {
            when(filter.apply(ctx)).thenReturn(ctx);
            processor.processFilter(ctx, filter, null);
            verify(filter, times(1)).apply(ctx);
        }

        @Test
        public void testProcessFilter_ShouldFilterFalse() throws Exception
        {
            when(filter.shouldFilter(ctx)).thenReturn(false);
            processor.processFilter(ctx, filter, null);
            verify(filter, times(0)).apply(ctx);
        }

        @Test
        public void testProcessFilter_AdditionalShouldFilterFalse() throws Exception
        {
            when(filter.shouldFilter(ctx)).thenReturn(true);
            when(additionalShouldFilter.shouldFilter(ctx)).thenReturn(false);
            processor.processFilter(ctx, filter, additionalShouldFilter);
            verify(filter, times(0)).apply(ctx);
        }

        @Test
        public void testProcessFilter_BothShouldFilterTrue() throws Exception
        {
            when(filter.shouldFilter(ctx)).thenReturn(true);
            when(additionalShouldFilter.shouldFilter(ctx)).thenReturn(true);
            processor.processFilter(ctx, filter, additionalShouldFilter);
            verify(filter, times(1)).apply(ctx);
        }



        @Test
        public void testProcessFilterException()
        {
            Exception e = new RuntimeException("Blah");
            when(filter.apply(ctx)).thenThrow(e);
            processor.processFilter(ctx, filter, null);

            verify(processor).recordFilterError(filter, ctx, e);

            ArgumentCaptor<FilterExecInfo> info = ArgumentCaptor.forClass(FilterExecInfo.class);
            verify(processor).recordFilterCompletion(same(ctx), same(filter), info.capture());
            assertEquals(ExecutionStatus.FAILED, info.getValue().status);
        }

        @Test
        public void testApplyAllFilters() throws Exception
        {
            Observable<SessionContext> chain = Observable.just(ctx);
            processor.applyAllFilters(chain);
            verify(processor, times(1)).applyFilterPhase(same(chain), eq("pre"), isNull(ShouldFilter.class));
            verify(processor, times(2)).applyFilterPhase(same(chain), eq("error"), isA(ErrorShouldFilter.class));
            verify(processor, times(1)).applyFilterPhase(same(chain), eq("route"), isA(RouteShouldFilter.class));
            verify(processor, times(2)).applyFilterPhase(same(chain), eq("error"), isA(ErrorShouldFilter.class));
            verify(processor, times(1)).applyFilterPhase(same(chain), eq("post"), isNull(ShouldFilter.class));
        }

        @Test
        public void testPostProcess()
        {
            Observable<SessionContext> chain = Observable.just(ctx);
            processor.applyFilterPhase(chain, "post", null);
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
