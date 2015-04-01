package com.netflix.zuul.lifecycle;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessage
{
    private final Headers headers;
    private byte[] body = null;

    public ZuulMessage() {
        this.headers = new Headers();
    }

    public ZuulMessage(Headers headers) {
        this.headers = headers;
    }

    public Headers getHeaders() {
        return headers;
    }

    public byte[] getBody()
    {
        return this.body;
    }

    public void setBody(byte[] body)
    {
        this.body = body;
    }
}
