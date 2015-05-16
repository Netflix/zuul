package com.netflix.zuul.filters;

import com.netflix.zuul.context.ZuulMessage;

/**
 * User: michaels@netflix.com
 * Date: 5/7/15
 * Time: 3:31 PM
 */
public interface ShouldFilter<T extends ZuulMessage>
{
    /**
     * a "true" return from this method means that the apply() method should be invoked
     *
     * @return true if the apply() method should be invoked. false will not invoke the apply() method
     */
    public boolean shouldFilter(T msg);
}
