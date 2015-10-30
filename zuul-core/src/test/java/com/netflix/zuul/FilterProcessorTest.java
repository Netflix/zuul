package com.netflix.zuul;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.*;
import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.filters.http.HttpOutboundFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.*;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * User: michaels@netflix.com
 * Date: 10/29/15
 * Time: 5:28 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class FilterProcessorTest
{
    @Mock
    BaseSyncFilter filter;
    @Mock
    FilterUsageNotifier usageNotifier;
    @Mock
    FilterLoader loader;
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

        processor = new FilterProcessor(loader, usageNotifier);
        processor = spy(processor);

        when(filter.filterType()).thenReturn("pre");
        when(filter.shouldFilter(request)).thenReturn(true);
        when(filter.getPriority()).thenReturn(5);
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

        ArgumentCaptor<FilterProcessor.FilterExecInfo> info = ArgumentCaptor.forClass(FilterProcessor.FilterExecInfo.class);
        verify(processor).recordFilterCompletion(same(request), same(filter), info.capture());
        assertEquals(ExecutionStatus.FAILED, info.getValue().status);
    }

    @Test
    public void testErrorInErrorEndpoint()
    {
        FilterLoader loader = new FilterLoader();

        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));
        addFilterToLoader(loader, new MockEndpointFilter(0, true, response));
        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        // Mock an error endpoint that in turn throws an exception when it is applied.
        String errorEndpointName = "endpoint.ErrorResponse";
        ZuulFilter errorEndpoint = mock(BaseFilter.class);
        when(errorEndpoint.filterName()).thenReturn(errorEndpointName);
        when(errorEndpoint.filterType()).thenReturn("end");
        when(errorEndpoint.filterOrder()).thenReturn(0);
        when(errorEndpoint.shouldFilter(any())).thenReturn(true);
        when(errorEndpoint.applyAsync(any())).thenThrow(new RuntimeException("Some error response problem."));
        loader.putFilter(errorEndpointName, errorEndpoint, 0);

        // Set this flag so that error endpoint is used.
        ctx.setShouldSendErrorResponse(true);

        FilterProcessor processor = new FilterProcessor(loader, usageNotifier);

        Observable<ZuulMessage> chain = Observable.just(request);
        chain = processor.applyInboundFilters(chain);
        chain = processor.applyEndpointFilter(chain);
        chain = processor.applyOutboundFilters(chain);

        ZuulMessage output = chain.toBlocking().single();

        // Should be only 1 errored filter.
        assertEquals(1, ctx.getFilterErrors().size());
        assertEquals(errorEndpointName, ctx.getFilterErrors().get(0).getFilterName());

        // Should be a default error response.
        HttpResponseMessage response = (HttpResponseMessage) output;
        assertEquals(500, response.getStatus());
    }


    @Test
    public void testErrorInErrorEndpoint_Async()
    {
        FilterLoader loader = new FilterLoader();

        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));
        addFilterToLoader(loader, new MockEndpointFilter(0, true, response));
        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        // Mock an error endpoint that in turn throws an exception when it is applied async.
        String errorEndpointName = "endpoint.ErrorResponse";
        ZuulFilter errorEndpoint = new Endpoint<HttpRequestMessage, HttpResponseMessage>()
        {
            @Override
            public String filterName()
            {
                return errorEndpointName;
            }

            @Override
            public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input)
            {
                return Observable.create(subscriber -> {
                    Throwable t = new RuntimeException("Some error response problem.");
                    subscriber.onError(t);
                });
            }
        };
        loader.putFilter(errorEndpointName, errorEndpoint, 0);

        // Set this flag so that error endpoint is used.
        ctx.setShouldSendErrorResponse(true);

        FilterProcessor processor = new FilterProcessor(loader, usageNotifier);

        Observable<ZuulMessage> chain = Observable.just(request);
        chain = processor.applyInboundFilters(chain);
        chain = processor.applyEndpointFilter(chain);
        chain = processor.applyOutboundFilters(chain);

        ZuulMessage output = chain.toBlocking().single();

        // Should be only 1 errored filter.
        assertEquals(1, ctx.getFilterErrors().size());
        assertEquals(errorEndpointName, ctx.getFilterErrors().get(0).getFilterName());

        // Should be a default error response.
        HttpResponseMessage response = (HttpResponseMessage) output;
        assertEquals(500, response.getStatus());
    }



    private void addFilterToLoader(FilterLoader loader, ZuulFilter f)
    {
        loader.putFilter(f.filterName(), f, 0);
    }

    private class MockHttpInboundFilter extends HttpInboundFilter
    {
        private int filterOrder;
        private boolean shouldFilter;

        public MockHttpInboundFilter(int filterOrder, boolean shouldFilter)
        {
            this.filterOrder = filterOrder;
            this.shouldFilter = shouldFilter;
        }

        @Override
        public String filterName()
        {
            return super.filterName() + "_" + filterOrder;
        }

        @Override
        public boolean shouldFilter(HttpRequestMessage msg)
        {
            return shouldFilter;
        }

        @Override
        public int filterOrder()
        {
            return filterOrder;
        }

        @Override
        public Observable<HttpRequestMessage> applyAsync(HttpRequestMessage input)
        {
            return Observable.just(input);
        }
    }


    private class MockHttpOutboundFilter extends HttpOutboundFilter
    {
        private int filterOrder;
        private boolean shouldFilter;

        public MockHttpOutboundFilter(int filterOrder, boolean shouldFilter)
        {
            this.filterOrder = filterOrder;
            this.shouldFilter = shouldFilter;
        }

        @Override
        public String filterName()
        {
            return super.filterName() + "_" + filterOrder;
        }

        @Override
        public boolean shouldFilter(HttpResponseMessage msg)
        {
            return shouldFilter;
        }

        @Override
        public int filterOrder()
        {
            return filterOrder;
        }

        @Override
        public Observable<HttpResponseMessage> applyAsync(HttpResponseMessage input)
        {
            return Observable.just(input);
        }
    }

    private class MockEndpointFilter extends Endpoint<HttpRequestMessage, HttpResponseMessage>
    {
        private int filterOrder;
        private boolean shouldFilter;
        private HttpResponseMessage response;

        public MockEndpointFilter(int filterOrder, boolean shouldFilter, HttpResponseMessage response)
        {
            this.filterOrder = filterOrder;
            this.shouldFilter = shouldFilter;
            this.response = response;
        }

        @Override
        public String filterName()
        {
            return super.filterName() + "_" + filterOrder;
        }

        @Override
        public boolean shouldFilter(HttpRequestMessage msg)
        {
            return shouldFilter;
        }

        @Override
        public int filterOrder()
        {
            return filterOrder;
        }

        @Override
        public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input)
        {
            return Observable.just(response);
        }
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
