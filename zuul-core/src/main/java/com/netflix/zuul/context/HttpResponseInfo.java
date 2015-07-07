package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 7/6/15
 * Time: 5:27 PM
 */
public class HttpResponseInfo
{
    private final int status;
    private final Headers headers;

    public HttpResponseInfo(int status, Headers headers)
    {
        this.status = status;
        this.headers = headers == null ? new Headers() : headers;
    }

    public int getStatus()
    {
        return status;
    }

    public Headers getHeaders()
    {
        return headers;
    }
}
