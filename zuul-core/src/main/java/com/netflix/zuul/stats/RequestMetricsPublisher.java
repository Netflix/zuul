package com.netflix.zuul.stats;

import com.netflix.zuul.context.SessionContext;

/**
 * User: michaels@netflix.com
 * Date: 3/9/15
 * Time: 5:56 PM
 */
public interface RequestMetricsPublisher
{
    void collectAndPublish(SessionContext context);
}
