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

import com.netflix.zuul.filter.Filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ZuulMetricsPublisherFactory {

    private static final ZuulMetricsPublisherFactory INSTANCE = new ZuulMetricsPublisherFactory();
    private static final AtomicReference<ZuulGlobalMetricsPublisher> globalPublisherRef = new AtomicReference<>(null);
    private static final Map<Class<? extends Filter>, ZuulFilterMetricsPublisher> filterPublishers = new ConcurrentHashMap<>();

    private final ZuulMetricsPublisher metricsPublisher;

    private ZuulMetricsPublisherFactory() {
        this(ZuulPlugins.getInstance().getMetricsPublisher());
    }

    private ZuulMetricsPublisherFactory(ZuulMetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    public static ZuulGlobalMetricsPublisher createOrRetrieveGlobalPublisher() {
        return INSTANCE.getGlobalPublisher();
    }

    private ZuulGlobalMetricsPublisher getGlobalPublisher() {
        //first look in atomic ref
        ZuulGlobalMetricsPublisher globalPublisher = globalPublisherRef.get();
        if (globalPublisher != null) {
            return globalPublisher;
        } else {
            //create a new one
            globalPublisher = metricsPublisher.getGlobalMetricsPublisher();
            if (globalPublisherRef.compareAndSet(null, globalPublisher)) {
                //we won the race
                globalPublisher.initialize();
                return globalPublisher;
            } else {
                //we lost the race
                return globalPublisherRef.get();
            }
        }
    }

    public static ZuulFilterMetricsPublisher createOrRetrieveFilterPublisher(Class<? extends Filter> filterClass) {
        return INSTANCE.getFilterPublisher(filterClass);
    }

    private ZuulFilterMetricsPublisher getFilterPublisher(Class<? extends Filter> filterClass) {
        //first look in cache
        ZuulFilterMetricsPublisher filterPublisher = filterPublishers.get(filterClass);
        if (filterPublisher != null) {
            return filterPublisher;
        } else {
            //create a new one
            filterPublisher = metricsPublisher.getFilterMetricsPublisher(filterClass);
            ZuulFilterMetricsPublisher existing = filterPublishers.putIfAbsent(filterClass, filterPublisher);
            if (existing == null) {
                //we won the race
                filterPublisher.initialize();
                return filterPublisher;
            } else {
                //we lost the race
                return existing;
            }
        }
    }
}
