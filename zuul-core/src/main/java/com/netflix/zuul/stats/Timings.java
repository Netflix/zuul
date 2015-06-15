package com.netflix.zuul.stats;

import java.util.concurrent.ConcurrentHashMap;

/**
 * User: michaels@netflix.com
 * Date: 6/15/15
 * Time: 4:16 PM
 */
public class Timings
{
    protected final ConcurrentHashMap<String, Timing> timings = new ConcurrentHashMap<>();

    public Timing get(String name)
    {
        return timings.computeIfAbsent(name, (newName) -> new Timing(newName));
    }

    /* Following are some standard Zuul timings: */

    public Timing getRequest()
    {
        return get("_requestTiming");
    }
    public Timing getRequestProxy()
    {
        return get("_requestProxyTiming");
    }
}
