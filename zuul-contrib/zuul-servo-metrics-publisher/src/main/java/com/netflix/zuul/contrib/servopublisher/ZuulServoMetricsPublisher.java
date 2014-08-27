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

import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.metrics.ZuulFilterMetricsPublisher;
import com.netflix.zuul.metrics.ZuulGlobalMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetricsPublisher;

/**
 * Servo implementation of {@link ZuulMetricsPublisher}.
 */
public class ZuulServoMetricsPublisher extends ZuulMetricsPublisher {

    private static ZuulServoMetricsPublisher INSTANCE = new ZuulServoMetricsPublisher();

    public static ZuulServoMetricsPublisher getInstance() {
        return INSTANCE;
    }

    private ZuulServoMetricsPublisher() {
    }

    @Override
    public ZuulGlobalMetricsPublisher getGlobalMetricsPublisher() {
        return new ZuulServoGlobalMetricsPublisher();
    }

    @Override
    public ZuulFilterMetricsPublisher getFilterMetricsPublisher(Class<? extends Filter> filterClass) {
        return new ZuulServoFilterMetricsPublisher(filterClass);
    }


}
