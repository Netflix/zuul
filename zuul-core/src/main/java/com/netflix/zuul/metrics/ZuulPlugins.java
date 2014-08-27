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

import java.util.concurrent.atomic.AtomicReference;

public class ZuulPlugins {
    private final static ZuulPlugins INSTANCE = new ZuulPlugins();

    final AtomicReference<ZuulMetricsPublisher> metricsPublisherRef =  new AtomicReference<>();

    private ZuulPlugins() {

    }

    public static ZuulPlugins getInstance() {
        return INSTANCE;
    }

    public ZuulMetricsPublisher getMetricsPublisher() {
        if (metricsPublisherRef.get() == null) {
            metricsPublisherRef.compareAndSet(null, new ZuulMetricsPublisher());
        }
        return metricsPublisherRef.get();
    }

    public void registerMetricsPublisher(ZuulMetricsPublisher impl) {
        if (!metricsPublisherRef.compareAndSet(null, impl)) {
            throw new IllegalStateException("Another metrics publisher already registered : " + metricsPublisherRef.get());
        }
    }
}
