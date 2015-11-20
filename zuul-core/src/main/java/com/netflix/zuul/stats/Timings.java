package com.netflix.zuul.stats;

import java.util.concurrent.ConcurrentHashMap;

/**
 * User: michaels@netflix.com
 * Date: 6/15/15
 * Time: 4:16 PM
 */
public class Timings
{
    private final Timing request = new Timing("_requestTiming");
    private final Timing requestProxy = new Timing("_requestProxyTiming");
    private final Timing requestBodyRead = new Timing("_requestBodyReadTiming");
    private final Timing responseBodyRead = new Timing("_responseBodyReadTiming");
    private final Timing requestBodyWrite = new Timing("_requestBodyWriteTiming");
    private final Timing responseBodyWrite = new Timing("_responseBodyWriteTiming");

    protected final ConcurrentHashMap<String, Timing> additionalTimings = new ConcurrentHashMap<>();

    public Timing get(String name)
    {
        return additionalTimings.computeIfAbsent(name, (newName) -> new Timing(newName));
    }

    /* Following are some standard Zuul timings: */

    public Timing getRequest()
    {
        return request;
    }
    public Timing getRequestProxy()
    {
        return requestProxy;
    }
    public Timing getRequestBodyRead()
    {
        return requestBodyRead;
    }
    public Timing getResponseBodyRead()
    {
        return responseBodyRead;
    }
    public Timing getRequestBodyWrite()
    {
        return requestBodyWrite;
    }
    public Timing getResponseBodyWrite()
    {
        return responseBodyWrite;
    }
}
