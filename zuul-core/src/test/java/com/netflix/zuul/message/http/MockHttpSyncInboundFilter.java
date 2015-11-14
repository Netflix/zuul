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

    public MockHttpSyncInboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpSyncInboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpSyncInboundFilter(String filterName, int filterOrder, boolean shouldFilter)
    {
        this(filterName, filterOrder, shouldFilter, null);
    }

    public MockHttpSyncInboundFilter(String filterName, int filterOrder, boolean shouldFilter, Throwable error)
    {
        this.filterName = filterName;
        this.filterOrder = filterOrder;
        this.shouldFilter = shouldFilter;
        this.error = error;
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
            throw new RuntimeException("Some error response problem.");
        }
        else {
            return input;
        }
    }
}
