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
package com.netflix.zuul.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic timing helper, in nanoseconds.
 *
 * User: michaels@netflix.com
 * Date: 12/13/13
 * Time: 5:05 PM
 */
public class Timing
{
    private static final Logger LOG = LoggerFactory.getLogger(Timing.class);

    private String name;
    private long startTime = 0;
    private long endTime = 0;
    private long duration = 0;

    public Timing(String name) {
        this.name = name;
    }

    public void start() {
        this.startTime = System.nanoTime();
    }

    public void end() {
        this.endTime = System.nanoTime();
        this.duration = endTime - startTime;

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Timing: name=%s, duration=%s", name, duration));
        }
    }

    public String getName() {
        return name;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String toString()
    {
        return Long.toString(duration);
    }
}
