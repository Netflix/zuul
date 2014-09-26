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
import com.netflix.numerus.NumerusRollingPercentile;
import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.lifecycle.IngressRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: propertize magic numbers
 * TODO: for now, we do nothing with Request - do we want to slice by URI or any other dimension on request?
 */
public class ZuulMetrics {

    private static final Logger logger = LoggerFactory.getLogger(ZuulMetrics.class);

    private static NumerusRollingNumber globalExecution = new NumerusRollingNumber(ZuulExecutionEvent.INIT, () -> 10000, () -> 100);
    private static NumerusRollingPercentile globalLatency = new NumerusRollingPercentile(() -> 10000, () -> 100, () -> 1000, () -> true);
    private static NumerusRollingNumber globalStatusCodeClass = new NumerusRollingNumber(ZuulStatusCodeClass.INIT, () -> 10000, () -> 100);
    private static NumerusRollingNumber globalStatusCode = new NumerusRollingNumber(ZuulStatusCode.INIT, () -> 10000, () -> 100);
    private static Map<Class<? extends Filter>, NumerusRollingNumber> filterExecutions = new ConcurrentHashMap<>();
    private static Map<Class<? extends Filter>, NumerusRollingPercentile> filterLatencies = new ConcurrentHashMap<>();

    public static void markSuccess(IngressRequest ingressReq, long duration) {
        globalExecution.increment(ZuulExecutionEvent.SUCCESS);
        globalLatency.addValue((int) duration);
    }

    public static void markError(IngressRequest ingressReq, long duration) {
        globalExecution.increment(ZuulExecutionEvent.FAILURE);
        globalLatency.addValue((int) duration);
    }

    public static void markStatus(int statusCode, IngressRequest ingressReq, long duration) {
        final ZuulStatusCode zuulStatusCode = ZuulStatusCode.from(statusCode);
        final ZuulStatusCodeClass zuulStatusCodeClass = zuulStatusCode.getStatusClass();

        globalStatusCode.increment(zuulStatusCode);
        globalStatusCodeClass.increment(zuulStatusCodeClass);

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

        NumerusRollingPercentile filterLatency = filterLatencies.get(filterClass);

        if (filterLatency != null) {
            filterLatency.addValue((int) duration);
        } else {
            NumerusRollingPercentile newFilterLatency = new NumerusRollingPercentile(() -> 10000, () -> 100, () -> 1000, () -> true);
            newFilterLatency.addValue((int) duration);
            filterLatencies.put(filterClass, newFilterLatency);
        }
    }

    public static NumerusRollingNumber getGlobalExecutionMetrics() {
        return globalExecution;
    }

    public static NumerusRollingNumber getGlobalStatusCodeClassMetrics() {
        return globalStatusCodeClass;
    }

    public static NumerusRollingNumber getGlobalStatusCodeMetrics() {
        return globalStatusCode;
    }

    public static NumerusRollingPercentile getGlobalLatencyMetrics() {
        return globalLatency;
    }

    public static NumerusRollingNumber getFilterExecutionMetrics(Class<? extends Filter> filterClass) {
        if (filterExecutions.get(filterClass) != null) {
            return filterExecutions.get(filterClass);
        } else {
            NumerusRollingNumber startingFilterExecution = new NumerusRollingNumber(ZuulExecutionEvent.INIT, () -> 10000, () -> 100);
            filterExecutions.put(filterClass, startingFilterExecution);
            return startingFilterExecution;
        }
    }

    public static NumerusRollingPercentile getFilterLatencyMetrics(Class<? extends Filter> filterClass) {
        if (filterLatencies.get(filterClass) != null) {
            return filterLatencies.get(filterClass);
        } else {
            NumerusRollingPercentile startingFilterLatency = new NumerusRollingPercentile(() -> 10000, () -> 100, () -> 1000, () -> true);
            filterLatencies.put(filterClass, startingFilterLatency);
            return startingFilterLatency;
        }
    }
}
