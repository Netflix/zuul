package com.netflix.zuul;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.*;
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

/**
 * User: michaels@netflix.com
 * Date: 10/29/15
 * Time: 5:28 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class ExperimentalFilterProcessor2Test
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

    ExperimentalFilterProcessor2 processor;

    @Before
    public void before() {
        MonitoringHelper.initMocks();
        MockitoAnnotations.initMocks(this);

        ctx = new SessionContext();
        request = new HttpRequestMessageImpl(ctx, "HTTP/1.1", "GET", "/somepath", new HttpQueryParams(),
                new Headers(), "127.0.0.1", "https", 80, "localhost");
        response = new HttpResponseMessageImpl(ctx, request, 200);

        processor = new ExperimentalFilterProcessor2(loader, usageNotifier);
        processor = spy(processor);

        when(filter.filterType()).thenReturn("pre");
        when(filter.shouldFilter(request)).thenReturn(true);
        when(filter.getPriority()).thenReturn(5);
    }

    @Test
    public void testProcessFilter() throws Exception {
        when(filter.applyAsync(request)).thenReturn(Observable.just(request));
        processor.processAsyncFilter(request, filter, false).toBlocking().first();
        verify(filter, times(1)).applyAsync(request);
    }

    @Test
    public void testProcessFilter_ShouldFilterFalse() throws Exception
    {
        when(filter.shouldFilter(request)).thenReturn(false);
        processor.processAsyncFilter(request, filter, false).toBlocking().first();
        verify(filter, times(0)).applyAsync(request);
    }

    @Test
    public void testProcessFilterException()
    {
        Exception e = new RuntimeException("Blah");
        when(filter.applyAsync(request)).thenThrow(e);
        when(filter.getDefaultOutput(any())).thenReturn(request);

        processor.processAsyncFilter(request, filter, false).toBlocking().first();

        verify(processor).recordFilterError(filter, request, e);

        ArgumentCaptor<FilterProcessor.FilterExecInfo> info = ArgumentCaptor.forClass(FilterProcessor.FilterExecInfo.class);
        verify(processor).recordFilterCompletion(same(request), same(filter), info.capture());
        assertEquals(ExecutionStatus.FAILED, info.getValue().status);
    }

    @Test
    public void testAllFiltersRan()
    {
        FilterLoader loader = new FilterLoader();

        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));

        response.setStatus(202);
        ZuulFilter endpoint = new MockEndpointFilter(true, response);
        addFilterToLoader(loader, endpoint);
        request.getContext().setEndpoint(endpoint.filterName());

        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        FilterProcessor processor = new FilterProcessor(loader, usageNotifier);

        Observable<ZuulMessage> chain = Observable.just(request);
        chain = processor.applyInboundFilters(chain);
        chain = processor.applyEndpointFilter(chain);
        chain = processor.applyOutboundFilters(chain);

        ZuulMessage output = chain.toBlocking().single();

        assertEquals(0, ctx.getFilterErrors().size());

        // TODO - how to count how many filters ran?
        assertEquals(5, ctx.getFilterExecutionSummary().toString().split(",").length);

        HttpResponseMessage response = (HttpResponseMessage) output;
        assertEquals(202, response.getStatus());
    }

    @Test
    public void testErrorInErrorEndpoint()
    {
        FilterLoader loader = new FilterLoader();

        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));
        addFilterToLoader(loader, new MockEndpointFilter(true, response));
        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        // Mock an error endpoint that in turn throws an exception when it is applied.
        String errorEndpointName = "endpoint.ErrorResponse";
        ZuulFilter errorEndpoint = new MockEndpointFilter(errorEndpointName, true, null, new RuntimeException("Some error response problem."));
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
        addFilterToLoader(loader, new MockEndpointFilter(true, response));
        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        // Mock an error endpoint that in turn throws an exception when it is applied async.
        String errorEndpointName = "endpoint.ErrorResponse";
        ZuulFilter errorEndpoint = new MockEndpointFilter(errorEndpointName, true, response, new RuntimeException("Some error response problem."));
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

}
