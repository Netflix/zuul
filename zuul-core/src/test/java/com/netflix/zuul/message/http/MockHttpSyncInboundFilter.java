package com.netflix.zuul.message.http;

import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:31 PM
 */
public class MockHttpSyncInboundFilter extends HttpInboundSyncFilter
{
    private String filterName;
    private int filterOrder;
    private boolean shouldFilter;
    private Throwable error;
    private boolean shouldSendErrorResponse;

    public MockHttpSyncInboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpSyncInboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpSyncInboundFilter(String filterName, int filterOrder, boolean shouldFilter)
    {
        this(filterName, filterOrder, shouldFilter, null, false);
    }

    public MockHttpSyncInboundFilter(String filterName, int filterOrder, boolean shouldFilter,
                                     Throwable error, boolean shouldSendErrorResponse)
    {
        this.filterName = filterName;
        this.filterOrder = filterOrder;
        this.shouldFilter = shouldFilter;
        this.error = error;
        this.shouldSendErrorResponse = shouldSendErrorResponse;
    }

    @Override
    public String filterName()
    {
        return filterName;
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
    public HttpRequestMessage apply(HttpRequestMessage input)
    {
        if (error != null) {
            if (shouldSendErrorResponse) input.getContext().setShouldSendErrorResponse(true);
            throw new RuntimeException("Some error response problem.");
        }
        else {
            return input;
        }
    }
}
