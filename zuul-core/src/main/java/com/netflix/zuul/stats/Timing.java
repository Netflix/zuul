package com.netflix.zuul.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
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
        this.startTime = System.currentTimeMillis();
    }

    public void end() {
        this.endTime = System.currentTimeMillis();
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
}
