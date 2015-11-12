package com.netflix.zuul.message.http;

import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:31 PM
 */
public class MockHttpInboundFilter extends HttpInboundFilter
{
    private String filterName;
    private int filterOrder;
    private boolean shouldFilter;

    public MockHttpInboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpInboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpInboundFilter(String filterName, int filterOrder, boolean shouldFilter)
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
