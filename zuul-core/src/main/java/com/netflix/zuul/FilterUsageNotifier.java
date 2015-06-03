package com.netflix.zuul;

import com.netflix.zuul.filters.ZuulFilter;

/**
 * Interface to implement for registering a callback for each time a filter
 * is used.
 *
 * User: michaels
 * Date: 5/13/14
 * Time: 9:55 PM
 */
public interface FilterUsageNotifier {
    public void notify(ZuulFilter filter, ExecutionStatus status);
}
