package com.netflix.zuul.message.http;

import com.netflix.zuul.filters.http.HttpOutboundFilter;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:32 PM
 */
public class MockHttpOutboundFilter extends HttpOutboundFilter
{
    private String filterName;
    private int filterOrder;
    private boolean shouldFilter;

    public MockHttpOutboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpOutboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpOutboundFilter(String filterName, int filterOrder, boolean shouldFilter)
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
    public Observable<HttpResponseMessage> applyAsync(HttpResponseMessage input)
    {
        return Observable.just(input);
    }
}
