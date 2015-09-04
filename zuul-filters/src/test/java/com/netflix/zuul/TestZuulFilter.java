package com.netflix.zuul;

import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.message.ZuulMessage;

public class TestZuulFilter extends BaseSyncFilter {

    public TestZuulFilter() {
        super();
    }

    @Override
    public String filterType() {
        return "test";
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
