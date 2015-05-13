package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessage implements Cloneable
{
    private Headers headers;
    private byte[] body = null;

    public ZuulMessage() {
        this.headers = new Headers();
    }

    public ZuulMessage(Headers headers) {
        this.headers = headers != null ? headers : new Headers();
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
        try {
            ZuulMessage copy = (ZuulMessage) super.clone();
            copy.headers = (Headers) this.headers.clone();
            return copy;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Should not happen.", e);
        }
    }
}
