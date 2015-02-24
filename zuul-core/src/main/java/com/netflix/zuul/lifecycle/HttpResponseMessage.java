package com.netflix.zuul.lifecycle;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpResponseMessage extends ZuulMessage
{
    private final HttpRequestMessage httpRequest;
    private int status;

    public HttpResponseMessage(HttpRequestMessage httpRequest, int defaultStatus)
    {
        this.httpRequest = httpRequest;
        this.status = defaultStatus;
    }

    public HttpRequestMessage getHttpRequest() {
        return httpRequest;
    }

    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }
}
