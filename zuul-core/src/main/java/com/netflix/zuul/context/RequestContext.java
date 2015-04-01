package com.netflix.zuul.context;


import com.netflix.zuul.lifecycle.ZuulMessage;
import com.netflix.zuul.metrics.Timing;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * NOTE: Not threadsafe, and not intended to be used concurrently.
 */
@SuppressWarnings("serial")
public class RequestContext
{
    private final ZuulMessage requestMessage;
    private final ZuulMessage responseMessage;
    private final HashMap<String, Object> attributes = new HashMap<>();
    private final HashMap<String, Object> helpers = new HashMap<>();

    private HttpServerRequest<ByteBuf> httpServerRequest;

    public RequestContext(ZuulMessage requestMessage, ZuulMessage responseMessage)
    {
        this.requestMessage = requestMessage;
        this.responseMessage = responseMessage;
    }

    public ZuulMessage getRequest() {
        return requestMessage;
    }

    public ZuulMessage getResponse() {
        return responseMessage;
    }

    public Map getHelpers() {
        return helpers;
    }

    public Map getAttributes() {
        return attributes;
    }


    // TODO - these methods are temp while I refactor the filter and Ingress/Egress interfaces.
    void internal_setHttpServerRequest(HttpServerRequest<ByteBuf> httpServerRequest) {
        this.httpServerRequest = httpServerRequest;
    }

    public HttpServerRequest<ByteBuf> internal_getHttpServerRequest() {
        return httpServerRequest;
    }


    /** Timers **/
    private Timing getTiming(String name)
    {
        Timing t = (Timing) attributes.get(name);
        if (t == null) {
            t = new Timing(name);
            attributes.put(name, t);
        }
        return t;
    }

    public Timing getRequestTiming()
    {
        return getTiming("_requestTiming");
    }
    public Timing getRequestProxyTiming()
    {
        return getTiming("_requestProxyTiming");
    }
    public void setOriginReportedDuration(int duration)
    {
        attributes.put("_originReportedDuration", duration);
    }
    public int getOriginReportedDuration()
    {
        Object value = attributes.get("_originReportedDuration");
        if (value != null) {
            return (Integer) value;
        }
        return -1;
    }
}
