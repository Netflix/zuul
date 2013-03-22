package com.netflix.zuul.stats;

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


    private final AtomicLong count = new AtomicLong();

    public NamedCountingMonitor(String name) {
        this.name = name;
    }

    public NamedCountingMonitor register() {
        MonitorRegistry.getInstance().registerObject(this);
        return this;
    }

    public long increment() {
        return this.count.incrementAndGet();
    }

    @Override
    public String getName() {
        return name;
    }

    public long getCount() {
        return this.count.get();
    }

}
