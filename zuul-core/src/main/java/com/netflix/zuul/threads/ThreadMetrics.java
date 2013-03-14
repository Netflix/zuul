package com.netflix.zuul.threads;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/20/12
 * Time: 3:13 PM
 * To change this template use File | Settings | File Templates.
 */

public abstract class ThreadMetrics implements Comparable<ThreadMetrics>{

    long waitTimePerSecond;
    long waitCountPerSecond;
    long blockedTimePerSecond;
    long blockedCountPerSecond;
    long threadTimePerSecond = 0;
    long threadUserTimePerSecond = 0;

    long lastUserTime = 0;
    long lastWaitTime;
    long lastWaitCount;
    long lastBlockedTime;
    long lastBlockedCount;
    long lastThreadTime = 0;

    @Override
    public int compareTo(ThreadMetrics t) {
        return (int)(t.threadTimePerSecond - threadTimePerSecond);
    }

    public long getWaitTimePerSecond() {
        return waitTimePerSecond;
    }

    public long getWaitCountPerSecond() {
        return waitCountPerSecond;
    }

    public long getBlockedTimePerSecond() {
        return blockedTimePerSecond;
    }

    public long getBlockedCountPerSecond() {
        return blockedCountPerSecond;
    }

    public long getThreadTimePerSecond() {
        return threadTimePerSecond;
    }

    public long getThreadUserTimePerSecond() {
        return threadUserTimePerSecond;
    }

    public long getLastUserTime() {
        return lastUserTime;
    }

    public long getLastWaitTime() {
        return lastWaitTime;
    }

    public long getLastWaitCount() {
        return lastWaitCount;
    }

    public long getLastBlockedTime() {
        return lastBlockedTime;
    }

    public long getLastBlockedCount() {
        return lastBlockedCount;
    }

    public long getLastThreadTime() {
        return lastThreadTime;
    }
}
