package com.netflix.zuul.metrics;

import com.netflix.zuul.context.RequestContext;

/**
 * User: michaels@netflix.com
 * Date: 3/9/15
 * Time: 5:56 PM
 */
public interface RequestMetricsPublisher
{
    void collectAndPublish(RequestContext context);
}
