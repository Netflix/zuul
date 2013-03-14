package com.netflix.zuul.threads;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/14/12
 * Time: 9:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class ThreadGroupData extends ThreadMetrics {

    String threadGroupName;

    List<ThreadData> groupThreads = null;

    void calculateThreadGroupTimes(String groupName, List<ThreadData> threads, long deltaTime){
        threadGroupName = groupName;

        long totalGroupThreadTime = 0;
        long totalGroupUserTime = 0;
        long totalGroupBlockTime= 0;
        long totalGroupWaitTime= 0;
        long totalGroupWaitCount= 0;
        long totalGroupBlockedCount= 0;


        for(ThreadData thread: threads){
            totalGroupBlockedCount +=thread.getBlockedCount();
            totalGroupBlockTime += thread.getBlockedTime();
            totalGroupThreadTime+= thread.lastThreadTime;
            totalGroupUserTime  += thread.lastUserTime;
            totalGroupWaitCount += thread.getWaitedCount();
            totalGroupWaitTime += thread.getWaitedTime();
        }

        if(lastThreadTime > 0){
            long deltaTtime = totalGroupThreadTime - lastThreadTime;
            threadTimePerSecond  = (deltaTtime / deltaTime)/1000000;
        }
        lastThreadTime = totalGroupThreadTime;


        if(lastUserTime > 0){
            long deltaTtime = totalGroupUserTime- lastUserTime;
            threadUserTimePerSecond   = (deltaTtime / deltaTime) /1000000;
        }
        lastUserTime = totalGroupUserTime;


        if(lastBlockedTime > 0){
            long deltaBlockedTime = totalGroupBlockTime - lastBlockedTime;
            blockedTimePerSecond = (deltaBlockedTime / deltaTime)/1000000;
        }
        lastBlockedTime = totalGroupBlockTime;

        if(lastBlockedCount > 0){
            long deltaBlockedCount = totalGroupBlockedCount - lastBlockedCount;
            blockedCountPerSecond= deltaBlockedCount / deltaTime;
        }
        lastBlockedCount= totalGroupBlockedCount;


        if(lastWaitTime > 0){
            long deltaWaitTime = totalGroupWaitTime- lastWaitTime;
            waitTimePerSecond = (deltaWaitTime/ deltaTime)/1000000;
        }
        lastWaitTime = totalGroupWaitTime;

        if(lastWaitCount > 0){
            long deltaWaitCount = totalGroupWaitCount - lastWaitCount;
            waitCountPerSecond = deltaWaitCount/ deltaTime;
        }
        lastWaitCount = totalGroupWaitCount;
        groupThreads = threads;
    }


    @Override
    public String toString() {
        return "ThreadGroup{" +
                "name=" + threadGroupName+
                ", waitTimePerSecond=" + waitTimePerSecond +
                ", waitCountPerSecond=" + waitCountPerSecond +
                ", blockedCountPerSecond=" + blockedCountPerSecond +
                ", blockedTimePerSecond=" + blockedTimePerSecond +
                ", threadTimePerSecond=" + threadTimePerSecond +
                ", threadUserTimePerSecond=" + threadUserTimePerSecond +
                '}';
    }

    public String getThreadGroupName() {
        return threadGroupName;
    }

    public List<ThreadData> getGroupThreads() {
        return groupThreads;
    }
}
