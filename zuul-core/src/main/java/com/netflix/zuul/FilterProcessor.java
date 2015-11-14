package com.netflix.zuul;

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
}
