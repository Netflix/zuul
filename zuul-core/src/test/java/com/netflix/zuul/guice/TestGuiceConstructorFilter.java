package com.netflix.zuul.guice;

import com.google.inject.Injector;
import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.message.ZuulMessage;
import javax.inject.Inject;

public class TestGuiceConstructorFilter extends BaseSyncFilter {

    final Injector injector;

    @Inject
    public TestGuiceConstructorFilter(Injector injector) {
        this.injector = injector;
    }

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