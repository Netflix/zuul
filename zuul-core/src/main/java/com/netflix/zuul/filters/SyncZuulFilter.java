package com.netflix.zuul.filters;

import com.netflix.zuul.message.ZuulMessage;

/**
 * User: michaels@netflix.com
 * Date: 11/16/15
 * Time: 2:07 PM
 */
public interface SyncZuulFilter<I extends ZuulMessage, O extends ZuulMessage> extends ZuulFilter<I, O>
{
    O apply(I input);
}
