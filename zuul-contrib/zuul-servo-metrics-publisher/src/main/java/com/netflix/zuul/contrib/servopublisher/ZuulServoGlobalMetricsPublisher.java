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
package com.netflix.zuul.contrib.servopublisher;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.Tag;
import com.netflix.servo.tag.TagList;
import com.netflix.zuul.metrics.ZuulExecutionEvent;
import com.netflix.zuul.metrics.ZuulGlobalMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetrics;
import com.netflix.zuul.metrics.ZuulStatusCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ZuulServoGlobalMetricsPublisher extends AbstractZuulServoMetricsPublisher implements ZuulGlobalMetricsPublisher {
    @Override
    public void initialize() {
        System.out.println("Initializing Servo publisher for Zuul global metrics");

        List<Monitor<?>> monitors = getServoMonitors();

        // publish metrics together under a single composite (it seems this name is ignored)
        MonitorConfig commandMetricsConfig = MonitorConfig.builder("Zuul").build();
        BasicCompositeMonitor commandMetricsMonitor = new BasicCompositeMonitor(commandMetricsConfig, monitors);

        DefaultMonitorRegistry.getInstance().register(commandMetricsMonitor);
    }

    protected Tag getServoTypeTag() {
        return new Tag() {
            @Override
            public String getKey() {
                return "type";
            }

            @Override
            public String getValue() {
                return "Zuul";
            }

            @Override
            public String tagString() {
                return "Zuul";
            }
        };
    }

    protected Tag getServoInstanceTag() {
        return new Tag() {
            @Override
            public String getKey() {
                return "instance";
            }

            @Override
            public String getValue() {
                return "Zuul";
            }

            @Override
            public String tagString() {
                return "Zuul";
            }
        };
    }

    private List<Monitor<?>> getServoMonitors() {

        List<Monitor<?>> monitors = new ArrayList<>();

        monitors.add(getCumulativeCountForEvent("countSuccess", ZuulMetrics.getGlobalExecutionMetrics(), ZuulExecutionEvent.SUCCESS));
        monitors.add(getCumulativeCountForEvent("countFailure", ZuulMetrics.getGlobalExecutionMetrics(), ZuulExecutionEvent.FAILURE));

        monitors.add(getCumulativeCountForEvent("count1xx", ZuulMetrics.getGlobalStatusCodeMetrics(), ZuulStatusCode.OneXX));
        monitors.add(getCumulativeCountForEvent("count2xx", ZuulMetrics.getGlobalStatusCodeMetrics(), ZuulStatusCode.TwoXX));
        monitors.add(getCumulativeCountForEvent("count3xx", ZuulMetrics.getGlobalStatusCodeMetrics(), ZuulStatusCode.ThreeXX));
        monitors.add(getCumulativeCountForEvent("count4xx", ZuulMetrics.getGlobalStatusCodeMetrics(), ZuulStatusCode.FourXX));
        monitors.add(getCumulativeCountForEvent("count5xx", ZuulMetrics.getGlobalStatusCodeMetrics(), ZuulStatusCode.FiveXX));
        monitors.add(getGaugeForEvent("latency_percentile_5", ZuulMetrics.getGlobalLatencyMetrics(), 5));
        monitors.add(getGaugeForEvent("latency_percentile_25", ZuulMetrics.getGlobalLatencyMetrics(), 25));
        monitors.add(getGaugeForEvent("latency_percentile_50", ZuulMetrics.getGlobalLatencyMetrics(), 50));
        monitors.add(getGaugeForEvent("latency_percentile_75", ZuulMetrics.getGlobalLatencyMetrics(), 75));
        monitors.add(getGaugeForEvent("latency_percentile_90", ZuulMetrics.getGlobalLatencyMetrics(), 90));
        monitors.add(getGaugeForEvent("latency_percentile_95", ZuulMetrics.getGlobalLatencyMetrics(), 95));
        monitors.add(getGaugeForEvent("latency_percentile_99", ZuulMetrics.getGlobalLatencyMetrics(), 99));
        monitors.add(getGaugeForEvent("latency_percentile_995", ZuulMetrics.getGlobalLatencyMetrics(), 99.5));

        return monitors;
    }

    @Override
    protected TagList getServoTags() {
        Tag servoTypeTag = new Tag() {
            @Override
            public String getKey() {
                return "type";
            }

            @Override
            public String getValue() {
                return "Zuul";
            }

            @Override
            public String tagString() {
                return "Zuul";
            }
        };

        return new BasicTagList(Arrays.asList(servoTypeTag));
    }
}
