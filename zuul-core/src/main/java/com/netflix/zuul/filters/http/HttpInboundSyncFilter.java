package com.netflix.zuul.filters.http;

import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.BaseSyncFilter;

/**
 * User: michaels@netflix.com
 * Date: 5/29/15
 * Time: 3:22 PM
 */
public abstract class HttpInboundSyncFilter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage>
{
    @Override
    public String filterType() {
        return "in";
    }
}
