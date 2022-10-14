package com.netflix.netty.common.metrics;

import com.google.common.truth.Truth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class CustomLeakDetector extends InstrumentedResourceLeakDetector {
    public static final List<CustomLeakDetector> GLOBAL_REGISTRY = new CopyOnWriteArrayList<>();

    public static void assertZeroLeaks() {
        List<CustomLeakDetector> leaks = GLOBAL_REGISTRY.stream()
                            .filter(detector -> detector.leakCounter.get() > 0)
                            .collect(Collectors.toList());
        Truth.assertThat(leaks).isEmpty();
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
        return "CustomLeakDetector: "
                + this.resourceTypeName
                + " leakCount="
                + leakCounter.get();
    }
}
