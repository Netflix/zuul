/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

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
