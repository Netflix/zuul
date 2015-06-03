package com.netflix.zuul.filters.http;

import com.netflix.zuul.context.HttpResponseMessage;
import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.BaseSyncFilter;

/**
 * User: michaels@netflix.com
 * Date: 5/29/15
 * Time: 3:23 PM
 */
public abstract class HttpOutboundSyncFilter extends BaseSyncFilter<HttpResponseMessage, HttpResponseMessage>
{
    @Override
    public String filterType() {
        return "out";
    }
}
