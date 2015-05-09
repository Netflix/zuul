package com.netflix.zuul;

import com.netflix.zuul.context.SessionContext;

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
     */
    public void handle(SessionContext context);
}

