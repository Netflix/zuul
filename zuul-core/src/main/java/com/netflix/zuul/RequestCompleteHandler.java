package com.netflix.zuul;

import com.netflix.zuul.context.HttpResponseMessage;

/**
 * User: michaels@netflix.com
 * Date: 3/13/15
 * Time: 5:39 PM
 */
public interface RequestCompleteHandler
{
    /**
     *
     * @param response HttpResponseMessage
     */
    public void handle(HttpResponseMessage response);
}

