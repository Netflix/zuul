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

package com.netflix.zuul.netty.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.config.DynamicIntProperty;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 4/29/16
 * Time: 10:23 AM
 */
@Singleton
public final class DirectMemoryMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(DirectMemoryMonitor.class);
    private static final String PROP_PREFIX = "zuul.directmemory";
    private static final DynamicIntProperty TASK_DELAY_PROP = new DynamicIntProperty(PROP_PREFIX + ".task.delay", 10);

    private static final Supplier<Long> directMemoryLimitGetter;
    private static final Supplier<Long> reservedMemoryGetter;

    static {
        Supplier<Long> directMemoryLimit;
        Supplier<Long> reservedMemory;
        try {
            Class<?> c = Class.forName("io.netty.util.internal.PlatformDependent");
            Field directMemoryLimitField = c.getDeclaredField("DIRECT_MEMORY_LIMIT");
            directMemoryLimitField.setAccessible(true);
            directMemoryLimit = () -> {
                try {
                    return (long) directMemoryLimitField.get(null);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            };
            // Check that the call works
            directMemoryLimit.get();
            Field reservedMemoryField = c.getDeclaredField("DIRECT_MEMORY_COUNTER");
            reservedMemoryField.setAccessible(true);
            reservedMemory = () -> {
                try {
                    AtomicLong value = (AtomicLong) reservedMemoryField.get(null);
                    // Matches behavior in PlatformDependent.usedDirectMemory.
                    return value == null ? -1 : value.get();
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            };
            if (reservedMemory.get() == -1) {
                // This can occur when using JDK 11, which can prevent some of the reflective operations
                // PlatformDependent depends on.
                LOG.debug("Unable to get direct memory");
            }
        } catch (Throwable t) {
            LOG.warn("Unable to query direct memory, disabling monitor", t);
            directMemoryLimit = null;
            reservedMemory = null;
        }
        directMemoryLimitGetter = directMemoryLimit;
        reservedMemoryGetter = reservedMemory;
    }

    // TODO(carl-mastrangelo): this should be passed in as a dependency, so it can be shutdown and waited on for
    //    termination.
    private final ScheduledExecutorService service =
            Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setDaemon(true).setNameFormat("dmm-%d").build());

    @Inject
    public DirectMemoryMonitor(Registry registry) {
        if (reservedMemoryGetter != null) {
            PolledMeter.using(registry)
                    .withName(PROP_PREFIX + ".reserved")
                    .withDelay(Duration.ofSeconds(TASK_DELAY_PROP.get()))
                    .scheduleOn(service)
                    .monitorValue(DirectMemoryMonitor.class, DirectMemoryMonitor::getReservedMemory);
        }
        if (directMemoryLimitGetter != null) {
            PolledMeter.using(registry)
                    .withName(PROP_PREFIX + ".max")
                    .withDelay(Duration.ofSeconds(TASK_DELAY_PROP.get()))
                    .scheduleOn(service)
                    .monitorValue(DirectMemoryMonitor.class, DirectMemoryMonitor::getMaxMemory);
        }
    }

    private static double getReservedMemory(Object discard) {
        try {
            return reservedMemoryGetter.get();
        } catch (RuntimeException | Error e) {
            LOG.warn("Error in DirectMemoryMonitor task.", e);
        }
        return -1;
    }

    private static double getMaxMemory(Object discard) {
        try {
            return directMemoryLimitGetter.get();
        } catch (RuntimeException | Error e) {
            LOG.warn("Error in DirectMemoryMonitor task.", e);
        }
        return -1;
    }
}
