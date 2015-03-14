package com.netflix.zuul;

import com.netflix.zuul.context.RequestContext;

/**
 * User: michaels@netflix.com
 * Date: 3/13/15
 * Time: 5:39 PM
 */
public interface RequestCompleteHandler
{
    /**
     *
     * @param context RequestContext
     * @param duration in nanoseconds
     * @param responseBodySize byte count of total response body actually written.
     */
    public void handle(RequestContext context, long duration, int responseBodySize);
}
