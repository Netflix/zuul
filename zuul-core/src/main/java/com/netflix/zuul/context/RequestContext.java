package com.netflix.zuul.context;


import com.netflix.zuul.lifecycle.ZuulMessage;
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

    private HttpServerRequest httpServerRequest;

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
    void internal_setHttpServerRequest(HttpServerRequest httpServerRequest) {
        this.httpServerRequest = httpServerRequest;
    }

    public HttpServerRequest internal_getHttpServerRequest() {
        return httpServerRequest;
    }
}
