/*
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.zuul.plugins;

import com.netflix.servo.monitor.DynamicTimer;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.tag.InjectableTag;
import com.netflix.servo.tag.Tag;
import com.netflix.zuul.monitoring.TracerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Plugin to hook up Servo Tracers
 *
 * @author Mikey Cohen
 *         Date: 4/10/13
 *         Time: 4:51 PM
 */
public class Tracer extends TracerFactory {

    static List<Tag> tags = new ArrayList<Tag>(2);

    static {
        tags.add(InjectableTag.HOSTNAME);
        tags.add(InjectableTag.IP);
    }

    @Override
    public com.netflix.zuul.monitoring.Tracer startMicroTracer(String name) {
        return new ServoTracer(name);
    }

    class ServoTracer implements com.netflix.zuul.monitoring.Tracer {

        final MonitorConfig config;
        final Stopwatch stopwatch;

        private ServoTracer(String name) {
            config = MonitorConfig.builder(name).withTags(tags).build();
            stopwatch = DynamicTimer.start(config, TimeUnit.MICROSECONDS);
        }

        @Override
        public void stopAndLog() {
            DynamicTimer.record(config, stopwatch.getDuration());
        }

        @Override
        public void setName(String name) {

        }
    }
}
