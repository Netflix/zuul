package com.netflix.zuul.filters;

import com.netflix.zuul.context.SessionContext;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 2:36 PM
 */
public interface SyncFilter extends ZuulFilter
{
    /**
     * if shouldFilter() is true, this method will be invoked. this method is the core method of a ZuulFilter
     */
    SessionContext apply(SessionContext ctx);
}
