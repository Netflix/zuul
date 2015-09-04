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
package com.netflix.zuul.stats;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.annotations.MonitorTags;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.TagList;
import com.netflix.zuul.stats.monitoring.MonitorRegistry;
import com.netflix.zuul.stats.monitoring.NamedCount;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple Epic counter with a name and a count.
 *
 * @author mhawthorne
 */
public class NamedCountingMonitor implements NamedCount{


    private final String name;

    @MonitorTags
    TagList tagList;

    @Monitor(name = "count", type = DataSourceType.COUNTER)
    private final AtomicLong count = new AtomicLong();

    public NamedCountingMonitor(String name) {
        this.name = name;
        tagList = BasicTagList.of("ID", name);
    }

    /**
     * reguisters this objects
     * @return
     */
    public NamedCountingMonitor register() {
        MonitorRegistry.getInstance().registerObject(this);
        return this;
    }

    /**
     * increments the counter
     * @return
     */
    public long increment() {
        return this.count.incrementAndGet();
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     *
     * @return the current count
     */
    public long getCount() {
        return this.count.get();
    }

}
