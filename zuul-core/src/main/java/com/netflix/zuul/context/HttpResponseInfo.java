package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 7/6/15
 * Time: 5:27 PM
 */
public interface HttpResponseInfo extends ZuulMessage
{
    int getStatus();
    Headers getHeaders();

    /** The mutable request that will be sent to Origin. */
    HttpRequestMessage getRequest();

    /** The immutable request that was originally received from client. */
    HttpRequestInfo getInboundRequest();

    HttpResponseInfo getInboundResponse();

    @Override
    ZuulMessage clone();

    @Override
    String getInfoForLogging();

    Cookies parseSetCookieHeader(String setCookieValue);
    boolean hasSetCookieWithName(String cookieName);
}
