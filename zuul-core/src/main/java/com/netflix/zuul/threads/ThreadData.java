package com.netflix.zuul.threads;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/14/12
 * Time: 9:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class ThreadData extends ThreadMetrics {

    private ThreadInfo currentThreadInfo;


    ThreadData addThreadInfo(ThreadInfo threadInfo, long deltaTime) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long lThreadTime = threadBean.getThreadCpuTime(threadInfo.getThreadId());
        long lThreadUserTime = threadBean.getThreadUserTime(threadInfo.getThreadId());
        if(lastThreadTime > 0){
            long deltaTtime = lThreadTime - lastThreadTime;
            threadTimePerSecond  = (deltaTtime / deltaTime)/1000;
        }
        lastThreadTime = lThreadTime;


        if(lastUserTime > 0){
            long deltaTtime = lThreadUserTime- lastUserTime;
            threadUserTimePerSecond   = (deltaTtime / deltaTime) /1000;
        }
        lastUserTime = lThreadUserTime;


        if(lastBlockedTime > 0){
            long deltaBlockedTime = threadInfo.getBlockedTime()- lastBlockedTime;
            blockedTimePerSecond = (deltaBlockedTime / deltaTime)/1000;
        }
        lastBlockedTime = threadInfo.getBlockedTime();

        if(lastBlockedCount > 0){
            long deltaBlockedCount = threadInfo.getBlockedCount()- lastBlockedCount;
            blockedCountPerSecond= deltaBlockedCount / deltaTime;
        }
        lastBlockedCount= threadInfo.getBlockedCount();


        if(lastWaitTime > 0){
            long deltaWaitTime = threadInfo.getWaitedTime()- lastWaitTime;
            waitTimePerSecond = (deltaWaitTime/ deltaTime)/1000;
        }
        lastWaitTime = threadInfo.getWaitedTime();

        if(lastWaitCount > 0){
            long deltaWaitCount = threadInfo.getWaitedCount()- lastWaitCount;
            waitCountPerSecond = deltaWaitCount/ deltaTime;
        }
        lastWaitCount = threadInfo.getWaitedCount();
        currentThreadInfo = threadInfo;
        return this;


    }

    public long getThreadId() {
        return currentThreadInfo.getThreadId();
    }

    public String getThreadName() {
        return currentThreadInfo.getThreadName();
    }

    public Thread.State getThreadState() {
        return currentThreadInfo.getThreadState();
    }

    public long getBlockedTime() {
        return currentThreadInfo.getBlockedTime();
    }

    public long getBlockedCount() {
        return currentThreadInfo.getBlockedCount();
    }

    public long getWaitedTime() {
        return currentThreadInfo.getWaitedTime();
    }

    public long getWaitedCount() {
        return currentThreadInfo.getWaitedCount();
    }

    public LockInfo getLockInfo() {
        return currentThreadInfo.getLockInfo();
    }

    public String getLockName() {
        return currentThreadInfo.getLockName();
    }

    public long getLockOwnerId() {
        return currentThreadInfo.getLockOwnerId();
    }

    public String getLockOwnerName() {
        return currentThreadInfo.getLockOwnerName();
    }

    public StackTraceElement[] getStackTrace() {
        return currentThreadInfo.getStackTrace();
    }

    public MonitorInfo[] getLockedMonitors() {
        return currentThreadInfo.getLockedMonitors();
    }

    public LockInfo[] getLockedSynchronizers() {
        return currentThreadInfo.getLockedSynchronizers();
    }

    @Override
    public String toString() {
        return "ThreadData{" +
                "ThreadName=" + getThreadName()+
                ", ThreadId=" + getThreadId()+
                ", state=" + getThreadState()+
                ", waitTimePerSecond=" + waitTimePerSecond +
                ", waitCountPerSecond=" + waitCountPerSecond +
                ", blockedCountPerSecond=" + blockedCountPerSecond +
                ", blockedTimePerSecond=" + blockedTimePerSecond +
                ", threadTimePerSecond=" + threadTimePerSecond +
                ", threadUserTimePerSecond=" + threadUserTimePerSecond +
                ", currentThreadInfo=" + currentThreadInfo +
                '}';
    }


}
