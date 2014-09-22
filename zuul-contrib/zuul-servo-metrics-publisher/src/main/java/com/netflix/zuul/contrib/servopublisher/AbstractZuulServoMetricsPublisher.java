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
import com.netflix.numerus.NumerusRollingNumberEvent;
import com.netflix.numerus.NumerusRollingPercentile;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Gauge;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;


public abstract class AbstractZuulServoMetricsPublisher {

    abstract protected Tag getServoClassTag();
    abstract protected Tag getServoIdTag();

    protected Monitor<?> getCumulativeCountForEvent(String name, final NumerusRollingNumber metric, final NumerusRollingNumberEvent event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoClassTag()).withTag(getServoIdTag()).build()) {
            @Override
            public Number getValue() {
                return metric.getCumulativeSum(event);
            }
        };
    }

    protected Monitor<?> getGaugeForEvent(String name, NumerusRollingPercentile metric, double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(getServoClassTag()).withTag(getServoIdTag()).build()) {
            @Override
            public Number getValue() {
                return metric.getPercentile(percentile);
            }
        };
    }

    protected abstract class CounterMetric extends AbstractMonitor<Number> implements Counter {
        public CounterMetric(MonitorConfig config) {
            super(config.withAdditionalTag(DataSourceType.COUNTER).withAdditionalTag(getServoClassTag()).withAdditionalTag(getServoIdTag()));
        }

        @Override
        public Number getValue(int n) {
            return getValue();
        }

        @Override
        public abstract Number getValue();

        @Override
        public void increment() {
            throw new IllegalStateException("We are wrapping a value instead.");
        }

        @Override
        public void increment(long arg0) {
            throw new IllegalStateException("We are wrapping a value instead.");
        }
    }

    protected abstract class GaugeMetric extends AbstractMonitor<Number> implements Gauge<Number> {
        public GaugeMetric(MonitorConfig config) {
            super(config.withAdditionalTag(DataSourceType.GAUGE).withAdditionalTag(getServoClassTag()).withAdditionalTag(getServoIdTag()));
        }

        @Override
        public Number getValue(int n) {
            return getValue();
        }

        @Override
        public abstract Number getValue();
    }
}
