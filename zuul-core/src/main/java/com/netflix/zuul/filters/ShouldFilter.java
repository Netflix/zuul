package com.netflix.zuul.filters;

import com.netflix.zuul.context.SessionContext;

/**
 * User: michaels@netflix.com
 * Date: 5/7/15
 * Time: 3:31 PM
 */
public interface ShouldFilter
{
    public boolean shouldFilter(SessionContext ctx);
}
