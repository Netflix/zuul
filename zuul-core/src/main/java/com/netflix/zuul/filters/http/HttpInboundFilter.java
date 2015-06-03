package com.netflix.zuul.filters.http;

import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.filters.BaseFilter;

/**
 * User: michaels@netflix.com
 * Date: 5/29/15
 * Time: 3:22 PM
 */
public abstract class HttpInboundFilter extends BaseFilter<HttpRequestMessage, HttpRequestMessage>
{
    @Override
    public String filterType() {
        return "in";
    }
}
