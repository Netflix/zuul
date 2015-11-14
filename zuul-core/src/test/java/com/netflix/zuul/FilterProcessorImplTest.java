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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

/**
 * User: michaels@netflix.com
 * Date: 10/29/15
 * Time: 5:28 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class FilterProcessorImplTest
{
    @Mock
    BaseSyncFilter filter;
    @Mock
    FilterUsageNotifier usageNotifier;
    @Mock
    ShouldFilter additionalShouldFilter;

    FilterLoader loader;
    SessionContext ctx;
    HttpRequestMessage request;
    HttpResponseMessage response;

    FilterProcessorImpl processor;

    @Before
    public void before() {
        MonitoringHelper.initMocks();
        MockitoAnnotations.initMocks(this);

        loader = new FilterLoader();

        ctx = new SessionContext();
        request = new HttpRequestMessageImpl(ctx, "HTTP/1.1", "GET", "/somepath", new HttpQueryParams(),
                new Headers(), "127.0.0.1", "https", 80, "localhost");
        response = new HttpResponseMessageImpl(ctx, request, 200);

        processor = new FilterProcessorImpl(loader, usageNotifier);
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

        ArgumentCaptor<FilterProcessorImpl.FilterExecInfo> info = ArgumentCaptor.forClass(FilterProcessorImpl.FilterExecInfo.class);
        verify(processor).recordFilterCompletion(same(request), same(filter), info.capture());
        assertEquals(ExecutionStatus.FAILED, info.getValue().status);
    }


    @Test
    public void testAllFiltersRan()
    {
        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));

        response.setStatus(202);
        ZuulFilter endpoint = new MockEndpointFilter(true, response);
        addFilterToLoader(loader, endpoint);
        request.getContext().setEndpoint(endpoint.filterName());

        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        FilterProcessor processor = new FilterProcessorImpl(loader, usageNotifier);

        Observable<ZuulMessage> chain = processor.applyFilterChain(request);
        ZuulMessage output = chain.toBlocking().single();

        assertEquals(0, ctx.getFilterErrors().size());

        // TODO - how to count how many filters ran?
        assertEquals(5, ctx.getFilterExecutionSummary().toString().split(",").length);

        HttpResponseMessage response = (HttpResponseMessage) output;
        assertEquals(202, response.getStatus());
    }


    @Test
    public void testOnlyOneEndpointApplied()
    {
        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));

        response.setStatus(202);
        request.getContext().setEndpoint("Endpoint2");

        // Add 3 endpoints, only the 2nd of which should be applied.
        ZuulFilter endpoint1 = new MockEndpointFilter("Endpoint1", true, response);
        addFilterToLoader(loader, endpoint1);

        ZuulFilter endpoint2 = new MockEndpointFilter("Endpoint2", true, response);
        addFilterToLoader(loader, endpoint2);

        ZuulFilter endpoint3 = new MockEndpointFilter("Endpoint3", true, response);
        addFilterToLoader(loader, endpoint3);

        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        Observable<ZuulMessage> chain = processor.applyFilterChain(request);
        ZuulMessage output = chain.toBlocking().single();

        assertEquals(0, ctx.getFilterErrors().size());

        String filterExecStr = ctx.getFilterExecutionSummary().toString();
        assertEquals(5, filterExecStr.split(",").length);

        assertTrue(filterExecStr.contains("Endpoint2"));
        assertFalse(filterExecStr.contains("Endpoint1"));
        assertFalse(filterExecStr.contains("Endpoint3"));

        HttpResponseMessage response = (HttpResponseMessage) output;
        assertEquals(202, response.getStatus());
    }

    @Test
    public void testErrorInErrorEndpoint()
    {
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

        FilterProcessor processor = new FilterProcessorImpl(loader, usageNotifier);

        Observable<ZuulMessage> chain = processor.applyFilterChain(request);
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

        FilterProcessor processor = new FilterProcessorImpl(loader, usageNotifier);

        Observable<ZuulMessage> chain = processor.applyFilterChain(request);
        ZuulMessage output = chain.toBlocking().single();

        // Should be only 1 errored filter.
        assertEquals(1, ctx.getFilterErrors().size());
        assertEquals(errorEndpointName, ctx.getFilterErrors().get(0).getFilterName());

        // Should be a default error response.
        HttpResponseMessage response = (HttpResponseMessage) output;
        assertEquals(500, response.getStatus());
    }


    @Test
    public void testErrorInEndpoint_Async()
    {
        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        // Mock an endpoint that throws an exception when it is applied async.
        String endpointName = "endpoint.ProxyEndpoint";
        ZuulFilter endpoint = new MockEndpointFilter(endpointName, true, response, new RuntimeException("Some proxying problem!"));
        addFilterToLoader(loader, endpoint);
        ctx.setEndpoint(endpointName);

        // Mock an error endpoint.
        ZuulFilter errorEndpoint = mock(ZuulFilter.class);
        when(errorEndpoint.filterName()).thenReturn("endpoint.ErrorResponse");
        when(errorEndpoint.filterType()).thenReturn("end");
        when(errorEndpoint.shouldFilter(request)).thenReturn(true);
        when(errorEndpoint.getPriority()).thenReturn(5);
        when(errorEndpoint.getDefaultOutput(any())).thenReturn(HttpResponseMessageImpl.defaultErrorResponse(request));
        when(errorEndpoint.applyAsync(any())).thenReturn(Observable.just(response));
        addFilterToLoader(loader, errorEndpoint);


        FilterProcessor processor = new FilterProcessorImpl(loader, usageNotifier);
        Observable<ZuulMessage> chain = processor.applyFilterChain(request);

        ZuulMessage output = chain.toBlocking().single();


        verify(errorEndpoint).applyAsync(any());

        // Should be only 1 errored filter.
        assertEquals(1, ctx.getFilterErrors().size());
        assertEquals(endpointName, ctx.getFilterErrors().get(0).getFilterName());

        // Should be a HttpResponseMessage out.
        assertEquals(HttpResponseMessageImpl.class, output.getClass());
    }


    @Test
    public void testErrorInEndpointAndErrorResponse()
    {
        addFilterToLoader(loader, new MockHttpInboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpInboundFilter(1, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(0, true));
        addFilterToLoader(loader, new MockHttpOutboundFilter(1, true));

        // Mock an endpoint that throws an exception when it is applied async.
        String endpointName = "endpoint.ProxyEndpoint";
        ctx.setEndpoint(endpointName);
        ZuulFilter endpoint = new MockEndpointFilter(endpointName, true, response, new RuntimeException("Some proxying problem!"));
        addFilterToLoader(loader, endpoint);

        // Mock an error endpoint that in turn throws an exception when it is applied async.
        String errorEndpointName = "endpoint.ErrorResponse";
        ZuulFilter errorEndpoint = new MockEndpointFilter(errorEndpointName, true, response, new RuntimeException("Some error response problem."));
        addFilterToLoader(loader, errorEndpoint);


        FilterProcessor processor = new FilterProcessorImpl(loader, usageNotifier);

        Observable<ZuulMessage> chain = processor.applyFilterChain(request);
        ZuulMessage output = chain.toBlocking().single();

        assertEquals(2, ctx.getFilterErrors().size());
        assertEquals(endpointName, ctx.getFilterErrors().get(0).getFilterName());
        assertEquals(errorEndpointName, ctx.getFilterErrors().get(1).getFilterName());

        // Should be a HttpResponseMessage out.
        assertEquals(HttpResponseMessageImpl.class, output.getClass());
        assertEquals(500, ((HttpResponseMessage) output).getStatus());
    }

    private void addFilterToLoader(FilterLoader loader, ZuulFilter f)
    {
        loader.putFilter(f.filterName(), f, 0);
    }
}
