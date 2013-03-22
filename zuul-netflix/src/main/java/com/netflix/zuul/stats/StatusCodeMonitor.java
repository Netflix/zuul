package com.netflix.zuul.stats;

import com.netflix.zuul.stats.monitoring.NamedCount;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks status code counts.
 *
 * @author mhawthorne
 */
public class StatusCodeMonitor implements NamedCount {


    private final AtomicLong count = new AtomicLong();


    private final int statusCode;

    public StatusCodeMonitor(int statusCode) {
        this.statusCode = statusCode;
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
