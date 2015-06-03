package com.netflix.zuul.context;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpResponseMessage extends ZuulMessage
{
    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.HttpResponseMessage.body.max.size", 25 * 1000 * 1024);

    private HttpRequestMessage request;
    private int status;

    public HttpResponseMessage(SessionContext context, HttpRequestMessage request, int defaultStatus)
    {
        super(context);
        this.request = request;
        this.status = defaultStatus;
    }

    public HttpResponseMessage(SessionContext context, Headers headers, HttpRequestMessage request, int status) {
        super(context, headers);
        this.request = request;
        this.status = status;
    }

    public HttpRequestMessage getRequest() {
        return request;
    }

    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }

    @Override
    public ZuulMessage clone()
    {
        return super.clone();
    }

    @Override
    public String getInfoForLogging()
    {
        StringBuilder sb = new StringBuilder()
                .append(getRequest().getInfoForLogging())
                .append(",proxy-status=").append(getStatus())
                ;
        return sb.toString();
    }
}
