package com.netflix.zuul;

import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 11/13/15
 * Time: 3:16 PM
 */
public interface FilterProcessor
{
    Observable<ZuulMessage> applyFilterChain(ZuulMessage msg);

    // TEMP while i'm comparing old and new impls. Remove when finished.
    Observable<ZuulMessage> processFilterAsObservable(Observable<ZuulMessage> input, ZuulFilter filter, boolean shouldSendErrorResponse);
}
