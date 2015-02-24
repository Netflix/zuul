package com.netflix.zuul.lifecycle;


import com.google.inject.Inject;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * NOTE: Not threadsafe, and not intended to be used concurrently.
 */
@SuppressWarnings("serial")
public class EdgeRequestContext
{
    @Inject
    private OriginManager originManager;

    private final ZuulMessage requestMessage;
    private final ZuulMessage responseMessage;
    private final HashMap<String, Object> attributes = new HashMap<>();
    private final HashMap<String, Object> helpers = new HashMap<>();

    private HttpServerRequest httpServerRequest;

    public EdgeRequestContext(ZuulMessage requestMessage, ZuulMessage responseMessage)
    {
        this.requestMessage = requestMessage;
        this.responseMessage = responseMessage;

        // Inject any helper objects into the context for use of filters.
        helpers.put("origin_manager", originManager);
    }

    public ZuulMessage getRequestMessage() {
        return requestMessage;
    }

    public ZuulMessage getResponseMessage() {
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
