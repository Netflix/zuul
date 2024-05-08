/*
 * Copyright 2022 Netflix, Inc.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class CustomLeakDetector extends InstrumentedResourceLeakDetector {
    private static final List<CustomLeakDetector> GLOBAL_REGISTRY = new CopyOnWriteArrayList<>();

    public static void assertZeroLeaks() {
        List<CustomLeakDetector> leaks = GLOBAL_REGISTRY.stream()
                .filter(detector -> detector.leakCounter.get() > 0)
                .collect(Collectors.toList());
        assertTrue(leaks.isEmpty(), "LEAKS DETECTED: " + leaks);
    }

    private final String resourceTypeName;

    public CustomLeakDetector(Class<?> resourceType, int samplingInterval) {
        super(resourceType, samplingInterval);
        this.resourceTypeName = resourceType.getSimpleName();
        GLOBAL_REGISTRY.add(this);
    }

    public CustomLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
        this(resourceType, samplingInterval);
    }

    @Override
    public String toString() {
        return "CustomLeakDetector: " + this.resourceTypeName + " leakCount=" + leakCounter.get();
    }
}
