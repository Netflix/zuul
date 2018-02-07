package com.netflix.zuul.exception;

import com.netflix.zuul.filters.ZuulFilter;

public class ZuulFilterConcurrencyExceededException extends ZuulException {

    public ZuulFilterConcurrencyExceededException(ZuulFilter filter, int concurrencyLimit) {
        super(filter.filterName() + " exceeded concurrency limit of " + concurrencyLimit, true);
    }
}
