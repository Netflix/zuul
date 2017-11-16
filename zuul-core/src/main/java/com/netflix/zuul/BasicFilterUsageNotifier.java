package com.netflix.zuul;

import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.zuul.filters.ZuulFilter;

/**
 * Publishes a counter metric for each filter on each use.
 */
public class BasicFilterUsageNotifier implements FilterUsageNotifier {
    private static final String METRIC_PREFIX = "zuul.filter-";

    @Override
    public void notify(ZuulFilter filter, ExecutionStatus status) {
        DynamicCounter.increment(METRIC_PREFIX + filter.getClass().getSimpleName(), "status", status.name(), "filtertype", filter.filterType().toString());
    }
}

