/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.zuul.metrics;

import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.zuul.Filter;
import com.netflix.zuul.IngressRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: propertize magic numbers
 * TODO: for now, we do nothing with Request - do we want to slice by URI or any other dimension on request?
 */
public class ZuulMetrics {

    private static NumerusRollingNumber globalExecution = new NumerusRollingNumber(ZuulExecutionEvent.INIT, () -> 10000, () -> 100);
    private static NumerusRollingNumber globalStatusCode = new NumerusRollingNumber(ZuulStatusCode.INIT, () -> 10000, () -> 100);
    private static Map<Class<? extends Filter>, NumerusRollingNumber> filterExecutions = new ConcurrentHashMap<>();

    public static void markSuccess(IngressRequest ingressReq, long duration) {
        globalExecution.increment(ZuulExecutionEvent.SUCCESS);
    }

    public static void markError(IngressRequest ingressReq, long duration) {
        globalExecution.increment(ZuulExecutionEvent.FAILURE);
    }

    public static void mark1xx(IngressRequest ingressReq, long duration) {
        markStatusCode(ingressReq, duration, ZuulStatusCode.OneXX);
    }

    public static void mark2xx(IngressRequest ingressReq, long duration) {
        markStatusCode(ingressReq, duration, ZuulStatusCode.TwoXX);
    }

    public static void mark3xx(IngressRequest ingressReq, long duration) {
        markStatusCode(ingressReq, duration, ZuulStatusCode.ThreeXX);
    }

    public static void mark4xx(IngressRequest ingressReq, long duration) {
        markStatusCode(ingressReq, duration, ZuulStatusCode.FourXX);
    }

    public static void mark5xx(IngressRequest ingressReq, long duration) {
        markStatusCode(ingressReq, duration, ZuulStatusCode.FiveXX);
    }

    private static void markStatusCode(IngressRequest ingressReq, long duration, ZuulStatusCode statusCodeType) {
        globalStatusCode.increment(statusCodeType);
    }

    public static void markFilterFailure(Class<? extends Filter> filterClass, long duration) {
        markFilter(filterClass, duration, ZuulExecutionEvent.FAILURE);
    }

    public static void markFilterSuccess(Class<? extends Filter> filterClass, long duration) {
        markFilter(filterClass, duration, ZuulExecutionEvent.SUCCESS);
    }

    public static void markFilterDisabled(Class<? extends Filter> filterClass, long duration) {
        markFilter(filterClass, duration, ZuulExecutionEvent.DISABLED);
    }

    private static void markFilter(Class<? extends Filter> filterClass, long duration, ZuulExecutionEvent executionType) {
        NumerusRollingNumber filterCounter = filterExecutions.get(filterClass);
        if (filterCounter != null) {
            filterCounter.increment(executionType);
        } else {
            NumerusRollingNumber newFilterCounter = new NumerusRollingNumber(executionType, () -> 10000, () -> 100);
            filterExecutions.put(filterClass, newFilterCounter);
        }
    }
}
