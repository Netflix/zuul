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

import com.netflix.config.DynamicIntProperty;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.LongGauge;
import com.netflix.servo.monitor.MonitorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: michaels@netflix.com
 * Date: 4/29/16
 * Time: 10:23 AM
 */
@Singleton
public class DirectMemoryMonitor
{
    private static final Logger LOG = LoggerFactory.getLogger(DirectMemoryMonitor.class);
    private static final String PROP_PREFIX = "zuul.directmemory";
    private static final DynamicIntProperty TASK_DELAY_PROP = new DynamicIntProperty(PROP_PREFIX + ".task.delay", 10);

    private final LongGauge reservedMemoryGauge = new LongGauge(MonitorConfig.builder(PROP_PREFIX + ".reserved").build());
    private final LongGauge maxMemoryGauge = new LongGauge(MonitorConfig.builder(PROP_PREFIX + ".max").build());

    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

    public DirectMemoryMonitor()
    {
        DefaultMonitorRegistry.getInstance().register(reservedMemoryGauge);
        DefaultMonitorRegistry.getInstance().register(maxMemoryGauge);
    }

    @PostConstruct
    public void init()
    {
        service.scheduleWithFixedDelay(new Task(), TASK_DELAY_PROP.get(), TASK_DELAY_PROP.get(), TimeUnit.SECONDS);
    }

    public void stop()
    {
        service.shutdown();
    }

    class Task implements Runnable
    {
        @Override
        public void run()
        {
            try {
                Current current = measure();
                if (current != null) {
                    LOG.debug("reservedMemory={}, maxMemory={}", current.reservedMemory, current.maxMemory);

                    reservedMemoryGauge.set(current.reservedMemory);
                    maxMemoryGauge.set(current.maxMemory);
                }
            }
            catch (Throwable t) {
                LOG.warn("Error in DirectMemoryMonitor task.", t);
            }
        }

        public Current measure()
        {
            try {
                Class c = Class.forName("io.netty.util.internal.PlatformDependent");
                Field maxMemory = c.getDeclaredField("DIRECT_MEMORY_LIMIT");
                maxMemory.setAccessible(true);
                Field reservedMemory = c.getDeclaredField("DIRECT_MEMORY_COUNTER");
                reservedMemory.setAccessible(true);
                Current current = new Current();
                synchronized (c) {
                    current.maxMemory = getMemoryValue(maxMemory);
                    current.reservedMemory = getMemoryValue(reservedMemory);
                }
                return current;
            }
            catch (Exception e) {
                LOG.warn("Error measuring direct memory.", e);
                return null;
            }
        }

        private Long getMemoryValue(Field field) throws IllegalAccessException
        {
            Object value = field.get(null);
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof AtomicLong) {
                return ((AtomicLong) value).get();
            } else {
                return null;
            }
        }
    }

    class Current
    {
        Long maxMemory;
        Long reservedMemory;
    }
}
