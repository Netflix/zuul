package com.netflix.zuul.context;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 6:45 PM
 */

import com.netflix.zuul.stats.Timing;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the context between client and origin server for the duration of the dedicated connection/session
 * between them. But we're currently still only modelling single request/response pair per session.
 *
 * NOTE: Not threadsafe, and not intended to be used concurrently.
 */
@SuppressWarnings("serial")
public class SessionContext implements Cloneable
{
    private final ZuulMessage requestMessage;
    private final ZuulMessage responseMessage;
    private final Attributes attributes;
    private final HashMap<String, Object> helpers = new HashMap<String, Object>();

    public SessionContext(ZuulMessage requestMessage, ZuulMessage responseMessage)
    {
        this.requestMessage = requestMessage;
        this.responseMessage = responseMessage;
        this.attributes = new Attributes();
    }

    private SessionContext(ZuulMessage requestMessage, ZuulMessage responseMessage, Attributes attributes)
    {
        this.requestMessage = requestMessage;
        this.responseMessage = responseMessage;
        this.attributes = attributes;
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

    public Attributes getAttributes() {
        return attributes;
    }


    /**
     * Makes a copy of the RequestContext. This is used for debugging.
     *
     * @return
     */
    @Override
    public Object clone()
    {
        HttpRequestMessage request = (HttpRequestMessage) this.requestMessage.clone();
        HttpResponseMessage response = (HttpResponseMessage) this.responseMessage.clone();
        Attributes attributes = this.getAttributes().copy();
        SessionContext copy = new SessionContext(request, response, attributes);

        // Don't copy the Helper objects.

        return copy;
    }

    /** Timers - TODO: remove the dedicated methods for these? */

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