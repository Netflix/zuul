package com.netflix.zuul.init;

import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.message.ZuulMessage;

public class TestZuulFilter extends BaseSyncFilter {

    @Override
    public FilterType filterType() {
        return FilterType.INBOUND;
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter(ZuulMessage msg) {
        return false;
    }

    @Override
    public ZuulMessage apply(ZuulMessage msg) {
        return null;
    }
}