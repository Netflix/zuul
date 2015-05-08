package com.netflix.zuul.filters;

import com.netflix.zuul.IZuulFilter;
import com.netflix.zuul.context.SessionContext;
import rx.Observable;

/**
 * Alternative interface to implement for filters doing IO.
 *
 * User: michaels@netflix.com
 * Date: 5/7/15
 * Time: 10:44 AM
 */
public interface AsyncFilter extends IZuulFilter
{
    Observable<SessionContext> applyAsync(SessionContext context);
}
