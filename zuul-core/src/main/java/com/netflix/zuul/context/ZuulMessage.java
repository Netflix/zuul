package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessage implements Cloneable
{
    private final SessionContext context;
    private final Headers headers;
    private byte[] body = null;

    public ZuulMessage(SessionContext context) {
        this(context, new Headers());
    }

    public ZuulMessage(SessionContext context, Headers headers) {
        this.context = context;
        this.headers = headers == null ? new Headers() : headers;
    }

    public SessionContext getContext() {
        return context;
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
    public ZuulMessage clone()
    {
        ZuulMessage copy = new ZuulMessage(context.clone(), headers.clone());
        copy.setBody(body.clone());
        return copy;
    }

    /**
     * Override this in more specific subclasses to add request/response info for logging purposes.
     *
     * @return
     */
    public String getInfoForLogging()
    {
        return "ZuulMessage";
    }
}
