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

package com.netflix.netty.common.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.util.ResourceLeakDetector;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pluggable ResourceLeakDetector to track metrics for leaks
 *
 * Author: Arthur Gonigberg
 * Date: September 20, 2016
 */
public class InstrumentedResourceLeakDetector<T> extends ResourceLeakDetector<T> {

    private final AtomicInteger instancesLeakCounter;
    @VisibleForTesting
    final AtomicInteger leakCounter;

    public InstrumentedResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
        super(resourceType, samplingInterval);

        this.instancesLeakCounter = SpectatorUtils.newGauge("NettyLeakDetector_instances", resourceType.getSimpleName(), new AtomicInteger());
        this.leakCounter = SpectatorUtils.newGauge("NettyLeakDetector", resourceType.getSimpleName(), new AtomicInteger());
    }

    public InstrumentedResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive)
    {
        this(resourceType, samplingInterval);
    }

    @Override
    protected void reportTracedLeak(String resourceType, String records) {
        super.reportTracedLeak(resourceType, records);
        leakCounter.incrementAndGet();
        resetReportedLeaks();
    }

    @Override
    protected void reportUntracedLeak(String resourceType) {
        super.reportUntracedLeak(resourceType);
        leakCounter.incrementAndGet();
        resetReportedLeaks();
    }

    /**
     * This private field in the superclass needs to be reset so that we can continue reporting leaks even
     * if they're duplicates. This is ugly but ideally should not be called frequently (or at all).
     */
    private void resetReportedLeaks() {
        try {
            Field reportedLeaks = ResourceLeakDetector.class.getDeclaredField("reportedLeaks");
            reportedLeaks.setAccessible(true);
            Object f = reportedLeaks.get(this);
            if (f instanceof Map) {
                ((Map) f).clear();
            }
        }
        catch (Throwable t) {
            // do nothing
        }
    }
}
