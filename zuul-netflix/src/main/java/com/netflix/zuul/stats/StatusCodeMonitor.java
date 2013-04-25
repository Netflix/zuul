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
import com.netflix.servo.tag.BasicTag;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.TagList;
import com.netflix.zuul.stats.monitoring.NamedCount;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks status code counts.
 *
 * @author mhawthorne
 */
public class StatusCodeMonitor implements NamedCount {

    @MonitorTags
    TagList tagList;

    @Monitor(name = "count", type = DataSourceType.COUNTER)
    private final AtomicLong count = new AtomicLong();

    @Monitor(name = "status_code", type = DataSourceType.INFORMATIONAL)
    private final int statusCode;

    public StatusCodeMonitor(int statusCode) {
        this.statusCode = statusCode;
        tagList = BasicTagList.of(new BasicTag(this.getClass().getName(), "" +statusCode));

    }

    @Override
    public String getName() {
        return ""+ statusCode;
    }


    public long getCount() {
        return this.count.get();
    }

    public void update() {
        count.incrementAndGet();
    }

}
