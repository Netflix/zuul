package com.netflix.zuul.message.http;

import com.netflix.zuul.filters.http.HttpOutboundSyncFilter;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:32 PM
 */
public class MockHttpSyncOutboundFilter extends HttpOutboundSyncFilter
{
    private String filterName;
    private int filterOrder;
    private boolean shouldFilter;

    public MockHttpSyncOutboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpOutboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpSyncOutboundFilter(String filterName, int filterOrder, boolean shouldFilter)
    {
        this.filterName = filterName;
        this.filterOrder = filterOrder;
        this.shouldFilter = shouldFilter;
    }

    @Override
    public String filterName()
    {
        return filterName;
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
    public HttpResponseMessage apply(HttpResponseMessage input)
    {
        return input;
    }
}
