package com.netflix.zuul.filters;

import com.netflix.zuul.context.SessionContext;
import rx.Observable;

/**
 * Alternative interface to implement for filters doing IO and returning an Observable
 * rather than blocking on the IO.
 *
 * User: michaels@netflix.com
 * Date: 5/7/15
 * Time: 10:44 AM
 */
public interface AsyncFilter extends ZuulFilter
{
    /**
     * if shouldFilter() is true, this method will be invoked. this method is the core method of a ZuulFilter
     */
    Observable<SessionContext> applyAsync(SessionContext context);
}
