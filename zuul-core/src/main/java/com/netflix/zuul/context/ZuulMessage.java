package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessage implements Cloneable
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

    @Override
    public Object clone()
    {
        Headers headersCopy = (Headers) headers.clone();
        ZuulMessage copy = new ZuulMessage(headersCopy);
        copy.setBody(getBody());

        return copy;
    }
}
