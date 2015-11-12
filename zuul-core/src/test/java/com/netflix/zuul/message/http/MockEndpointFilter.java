package com.netflix.zuul.message.http;

import com.netflix.zuul.filters.Endpoint;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:33 PM
 */
public class MockEndpointFilter extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    private String filterName;
    private boolean shouldFilter;
    private HttpResponseMessage response;

    public MockEndpointFilter(boolean shouldFilter)
    {
        this("MockEndpointFilter", shouldFilter, null);
    }

    public MockEndpointFilter(boolean shouldFilter, HttpResponseMessage response)
    {
        this("MockEndpointFilter", shouldFilter, response);
    }

    public MockEndpointFilter(String filterName, boolean shouldFilter, HttpResponseMessage response)
    {
        this.filterName = filterName;
        this.shouldFilter = shouldFilter;
        this.response = response;
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
        return 0;
    }

    @Override
    public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input)
    {
        if (response == null) {
            return Observable.just(new HttpResponseMessageImpl(input.getContext(), input, 200));
        }
        else {
            return Observable.just(response);
        }
    }
}
