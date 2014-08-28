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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;
import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.lifecycle.IngressRequest;

/**
 * TODO: propertize magic numbers
 * TODO: for now, we do nothing with Request - do we want to slice by URI or any other dimension on request?
 */
public class ZuulMetrics {

    private static NumerusRollingNumber globalExecution = new NumerusRollingNumber(ZuulExecutionEvent.INIT, () -> 10000, () -> 100);
    private static NumerusRollingPercentile globalLatency = new NumerusRollingPercentile(() -> 10000, () -> 100, () -> 1000, () -> true);
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

        NumerusRollingPercentile filterLatency = filterLatencies.get(filterClass);

        if (filterLatency != null) {
            filterLatency.addValue((int) duration);
        } else {
            NumerusRollingPercentile newFilterLatency = new NumerusRollingPercentile(() -> 10000, () -> 100, () -> 1000, () -> true);
            newFilterLatency.addValue((int) duration);
            filterLatencies.put(filterClass, newFilterLatency);
        }
    }

    public static void reportMetrics(int pollInMs) {
        Thread metricsThread = new MetricPollingThread(pollInMs);
        metricsThread.start();
    }

    private static class MetricPollingThread extends Thread {

        final int pollInMs;

        public MetricPollingThread(int pollInMs) {
            this.pollInMs = pollInMs;
        }

        @Override
        public void run() {
            System.out.println("Metrics thread started : " + getName());
            while(!Thread.currentThread().isInterrupted()) {
                StringBuffer buff = new StringBuffer();
                buff.append("Metrics at : ").append(System.currentTimeMillis()).append("\n");
                try {
                    buff.append("** GLOBAL Success ").append(globalExecution.getCumulativeSum(ZuulExecutionEvent.SUCCESS)).append(", Last 10s : ").append(globalExecution.getRollingSum(ZuulExecutionEvent.SUCCESS)).append("\n");
                    buff.append("** GLOBAL Failure ").append(globalExecution.getCumulativeSum(ZuulExecutionEvent.FAILURE)).append(", Last 10s : ").append(globalExecution.getRollingSum(ZuulExecutionEvent.FAILURE)).append("\n");
                    buff.append("** GLOBAL 1xx ").append(globalStatusCode.getCumulativeSum(ZuulStatusCode.OneXX)).append(", Last 10s : ").append(globalStatusCode.getRollingSum(ZuulStatusCode.OneXX)).append("\n");
                    buff.append("** GLOBAL 2xx ").append(globalStatusCode.getCumulativeSum(ZuulStatusCode.TwoXX)).append(", Last 10s : ").append(globalStatusCode.getRollingSum(ZuulStatusCode.TwoXX)).append("\n");
                    buff.append("** GLOBAL 3xx ").append(globalStatusCode.getCumulativeSum(ZuulStatusCode.ThreeXX)).append(", Last 10s : ").append(globalStatusCode.getRollingSum(ZuulStatusCode.ThreeXX)).append("\n");
                    buff.append("** GLOBAL 4xx ").append(globalStatusCode.getCumulativeSum(ZuulStatusCode.FourXX)).append(", Last 10s : ").append(globalStatusCode.getRollingSum(ZuulStatusCode.FourXX)).append("\n");
                    buff.append("** GLOBAL 5xx ").append(globalStatusCode.getCumulativeSum(ZuulStatusCode.FiveXX)).append(", Last 10s : ").append(globalStatusCode.getRollingSum(ZuulStatusCode.FiveXX)).append("\n");
                    buff.append("** GLOBAL Latency : 50p ").append(globalLatency.getPercentile(50.0)).append(", 90p ").append(globalLatency.getPercentile(90.0)).append(", 99p ").append(globalLatency.getPercentile(99.0)).append("\n");

                    for (Class<? extends Filter> filterClass: filterExecutions.keySet()) {
                        NumerusRollingNumber counter = filterExecutions.get(filterClass);
                        NumerusRollingPercentile percentile = filterLatencies.get(filterClass);
                        buff.append("** ").append(filterClass.getSimpleName()).append(" SUCCESS ").append(counter.getCumulativeSum(ZuulExecutionEvent.SUCCESS)).append(", FAILURE ").append(counter.getCumulativeSum(ZuulExecutionEvent.FAILURE)).append(", MEDIAN ").append(percentile.getPercentile(50.0)).append(", 90p ").append(percentile.getPercentile(90.0)).append("\n");
                    }
                    System.out.println(buff.toString());
                    Thread.sleep(pollInMs);
                } catch (InterruptedException ex) {
                    interrupt();
                }
            }
        }
    }
}
