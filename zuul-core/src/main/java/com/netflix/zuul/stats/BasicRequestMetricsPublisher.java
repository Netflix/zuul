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

import com.netflix.servo.monitor.DynamicTimer;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.zuul.context.SessionContext;

/**
 * User: michaels@netflix.com
 * Date: 6/4/15
 * Time: 4:22 PM
 */
public class BasicRequestMetricsPublisher implements RequestMetricsPublisher
{
    @Override
    public void collectAndPublish(SessionContext context)
    {
        // Request timings.
        long totalRequestTime = context.getTimings().getRequest().getDuration();
        long requestProxyTime = context.getTimings().getRequestProxy().getDuration();
        int originReportedDuration = context.getOriginReportedDuration();

        // Approximation of time spent just within Zuul's own processing of the request.
        long totalInternalTime = totalRequestTime - requestProxyTime;

        // Approximation of time added to request by addition of Zuul+NIWS
        // (ie. the time added compared to if ELB sent request direct to Origin).
        // if -1, means we don't have that metric.
        long totalTimeAddedToOrigin = -1;
        if (originReportedDuration > -1) {
            totalTimeAddedToOrigin = totalRequestTime - originReportedDuration;
        }

        // Publish
        final String METRIC_TIMINGS_REQ_PREFIX = "zuul.timings.request.";
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "total", totalRequestTime);
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "proxy", requestProxyTime);
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "internal", totalInternalTime);
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "added", totalTimeAddedToOrigin);
    }

    private void recordRequestTiming(String name, long timeNs)
    {
        long timeMs = timeNs / 1000000;
        if(timeMs > -1) {
            DynamicTimer.record(MonitorConfig.builder(name).build(), timeMs);
        }
    }
}
