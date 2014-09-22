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
import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.metrics.ZuulExecutionEvent;
import com.netflix.zuul.metrics.ZuulFilterMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ZuulServoFilterMetricsPublisher extends AbstractZuulServoMetricsPublisher implements ZuulFilterMetricsPublisher {
    private final static Logger logger = LoggerFactory.getLogger(ZuulServoFilterMetricsPublisher.class);

    private final Class<? extends Filter> filterClass;
    private final Tag servoClassTag;
    private final Tag servoIdTag;

    public ZuulServoFilterMetricsPublisher(Class<? extends Filter> filterClass) {
        this.filterClass = filterClass;
        this.servoClassTag = new Tag() {

            @Override
            public String getKey() {
                return "class";
            }

            @Override
            public String getValue() {
                return "ZuulFilter";
            }

            @Override
            public String tagString() {
                return "ZuulFilter";
            }
        };

        this.servoIdTag = new Tag() {

            @Override
            public String getKey() {
                return "id";
            }

            @Override
            public String getValue() {
                return filterClass.getSimpleName();
            }

            @Override
            public String tagString() {
                return filterClass.getSimpleName();
            }
        };
    }

    @Override
    public void initialize() {
        logger.info("Initializing Servo publishing for filter : " + filterClass.getSimpleName());

        List<Monitor<?>> monitors = getServoMonitors();

        // publish metrics together under a single composite (it seems this name is ignored)
        MonitorConfig commandMetricsConfig = MonitorConfig.builder("Zuul").build();
        BasicCompositeMonitor commandMetricsMonitor = new BasicCompositeMonitor(commandMetricsConfig, monitors);

        DefaultMonitorRegistry.getInstance().register(commandMetricsMonitor);
    }

    private List<Monitor<?>> getServoMonitors() {
        List<Monitor<?>> monitors = new ArrayList<>();

        NumerusRollingNumber filterExecutionMetrics = ZuulMetrics.getFilterExecutionMetrics(filterClass);
        if (filterExecutionMetrics != null) {
            monitors.add(getCumulativeCountForEvent("countSuccess", filterExecutionMetrics, ZuulExecutionEvent.SUCCESS));
            monitors.add(getCumulativeCountForEvent("countFailure", filterExecutionMetrics, ZuulExecutionEvent.FAILURE));
        }

        NumerusRollingPercentile filterLatencyMetrics = ZuulMetrics.getFilterLatencyMetrics(filterClass);
        if (filterLatencyMetrics != null) {
            monitors.add(getGaugeForEvent("latency_percentile_5", filterLatencyMetrics, 5));
            monitors.add(getGaugeForEvent("latency_percentile_25", filterLatencyMetrics, 25));
            monitors.add(getGaugeForEvent("latency_percentile_50", filterLatencyMetrics, 50));
            monitors.add(getGaugeForEvent("latency_percentile_75", filterLatencyMetrics, 75));
            monitors.add(getGaugeForEvent("latency_percentile_90", filterLatencyMetrics, 90));
            monitors.add(getGaugeForEvent("latency_percentile_95", filterLatencyMetrics, 95));
            monitors.add(getGaugeForEvent("latency_percentile_99", filterLatencyMetrics, 99));
            monitors.add(getGaugeForEvent("latency_percentile_995", filterLatencyMetrics, 99.5));
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
