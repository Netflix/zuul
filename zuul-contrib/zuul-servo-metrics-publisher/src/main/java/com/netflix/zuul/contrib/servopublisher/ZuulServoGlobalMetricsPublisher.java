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

import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;
import com.netflix.zuul.metrics.ZuulExecutionEvent;
import com.netflix.zuul.metrics.ZuulGlobalMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetrics;
import com.netflix.zuul.metrics.ZuulStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ZuulServoGlobalMetricsPublisher extends AbstractZuulServoMetricsPublisher implements ZuulGlobalMetricsPublisher {
    private final static Logger logger = LoggerFactory.getLogger(ZuulServoGlobalMetricsPublisher.class);

    private final Tag servoClassTag;
    private final Tag servoIdTag;

    public ZuulServoGlobalMetricsPublisher() {
        this.servoClassTag = new Tag() {

            @Override
            public String getKey() {
                return "class";
            }

            @Override
            public String getValue() {
                return "ZuulExecution";
            }

            @Override
            public String tagString() {
                return "ZuulExecution";
            }
        };

        this.servoIdTag = new Tag() {

            @Override
            public String getKey() {
                return "id";
            }

            @Override
            public String getValue() {
                return "global";
            }

            @Override
            public String tagString() {
                return "global";
            }
        };
    }

    @Override
    public void initialize() {
        logger.info("Initializing Servo publisher for Zuul global metrics");

        List<Monitor<?>> monitors = getServoMonitors();

        // publish metrics together under a single composite (it seems this name is ignored)
        MonitorConfig globalMetricsConfig = MonitorConfig.builder("ZuulExecution").build();

        BasicCompositeMonitor globalMetricsMonitor = new BasicCompositeMonitor(globalMetricsConfig, monitors);

        DefaultMonitorRegistry.getInstance().register(globalMetricsMonitor);
    }

    private List<Monitor<?>> getServoMonitors() {
        List<Monitor<?>> monitors = new ArrayList<>();

        NumerusRollingNumber globalExecutionMetrics = ZuulMetrics.getGlobalExecutionMetrics();
        if (globalExecutionMetrics != null) {
            monitors.add(getCumulativeCountForEvent("countSuccess", globalExecutionMetrics, ZuulExecutionEvent.SUCCESS));
            monitors.add(getCumulativeCountForEvent("countFailure", globalExecutionMetrics, ZuulExecutionEvent.FAILURE));
        }

        NumerusRollingNumber globalStatusCodeMetrics = ZuulMetrics.getGlobalStatusCodeMetrics();
        if (globalStatusCodeMetrics != null) {
            monitors.add(getCumulativeCountForEvent("count1xx", globalStatusCodeMetrics, ZuulStatusCode.OneXX));
            monitors.add(getCumulativeCountForEvent("count2xx", globalStatusCodeMetrics, ZuulStatusCode.TwoXX));
            monitors.add(getCumulativeCountForEvent("count3xx", globalStatusCodeMetrics, ZuulStatusCode.ThreeXX));
            monitors.add(getCumulativeCountForEvent("count4xx", globalStatusCodeMetrics, ZuulStatusCode.FourXX));
            monitors.add(getCumulativeCountForEvent("count5xx", globalStatusCodeMetrics, ZuulStatusCode.FiveXX));
        }

        NumerusRollingPercentile globalLatencyMetrics = ZuulMetrics.getGlobalLatencyMetrics();
        if (globalLatencyMetrics != null) {
            monitors.add(getGaugeForEvent("latency_percentile_5", globalLatencyMetrics, 5));
            monitors.add(getGaugeForEvent("latency_percentile_25", globalLatencyMetrics, 25));
            monitors.add(getGaugeForEvent("latency_percentile_50", globalLatencyMetrics, 50));
            monitors.add(getGaugeForEvent("latency_percentile_75", globalLatencyMetrics, 75));
            monitors.add(getGaugeForEvent("latency_percentile_90", globalLatencyMetrics, 90));
            monitors.add(getGaugeForEvent("latency_percentile_95", globalLatencyMetrics, 95));
            monitors.add(getGaugeForEvent("latency_percentile_99", globalLatencyMetrics, 99));
            monitors.add(getGaugeForEvent("latency_percentile_995", globalLatencyMetrics, 99.5));
        }

        return monitors;
    }

    @Override
    protected Tag getServoClassTag() {
        return servoClassTag;
    }

    @Override
    protected Tag getServoIdTag() {
        return servoIdTag;
    }
}
