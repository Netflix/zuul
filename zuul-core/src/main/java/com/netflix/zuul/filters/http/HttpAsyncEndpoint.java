package com.netflix.zuul.filters.http;

import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 10:36 PM
 */
public abstract class HttpAsyncEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    @Override
    public HttpResponseMessage getDefaultOutput(HttpRequestMessage request)
    {
        return HttpResponseMessageImpl.defaultErrorResponse(request);
    }
}
