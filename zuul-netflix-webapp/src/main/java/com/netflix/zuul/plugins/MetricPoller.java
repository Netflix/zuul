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

import com.netflix.servo.publish.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author Mikey Cohen
 * Date: 4/18/13
 * Time: 9:20 AM
 */
public class MetricPoller {

    final static PollScheduler scheduler = PollScheduler.getInstance();

    public static void startPoller(){
        scheduler.start();
        final int heartbeatInterval = 1200;
        MetricObserver transform = new CounterToRateMetricTransform(
                new FileMetricObserver("ZuulMetrics", new File(".")),
                heartbeatInterval, TimeUnit.SECONDS);

        PollRunnable task = new PollRunnable(
                new MonitorRegistryMetricPoller(),
                BasicMetricFilter.MATCH_ALL,
                transform);

        final int samplingInterval = 10;
        scheduler.addPoller(task, samplingInterval, TimeUnit.SECONDS);

    }

}
