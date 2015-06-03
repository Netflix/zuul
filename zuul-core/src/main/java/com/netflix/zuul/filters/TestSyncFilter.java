package com.netflix.zuul.filters;

import com.netflix.zuul.context.HttpRequestMessage;

/**
 * User: michaels@netflix.com
 * Date: 5/15/15
 * Time: 10:54 AM
 */
public class TestSyncFilter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage>
{
    @Override
    public HttpRequestMessage apply(HttpRequestMessage input) {
        return input;
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public String filterType() {
        return "in";
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage msg) {
        return true;
    }
}
