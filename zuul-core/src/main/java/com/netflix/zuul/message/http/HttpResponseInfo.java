package com.netflix.zuul.message.http;

import com.netflix.zuul.message.ZuulMessage;

/**
 * User: michaels@netflix.com
 * Date: 7/6/15
 * Time: 5:27 PM
 */
public interface HttpResponseInfo extends ZuulMessage
{
    int getStatus();

    /** The immutable request that was originally received from client. */
    HttpRequestInfo getInboundRequest();

    @Override
    ZuulMessage clone();

    @Override
    String getInfoForLogging();

    Cookies parseSetCookieHeader(String setCookieValue);
    boolean hasSetCookieWithName(String cookieName);
}
