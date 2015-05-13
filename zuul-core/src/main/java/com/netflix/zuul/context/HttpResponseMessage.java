package com.netflix.zuul.context;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpResponseMessage extends ZuulMessage
{
    private int status;

    public HttpResponseMessage(int defaultStatus)
    {
        this.status = defaultStatus;
    }

    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public Object clone()
    {
        return super.clone();
    }
}
