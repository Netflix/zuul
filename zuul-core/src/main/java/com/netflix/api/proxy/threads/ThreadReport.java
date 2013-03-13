package com.netflix.api.proxy.threads;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/23/12
 * Time: 10:53 AM
 * To change this template use File | Settings | File Templates.
 */

public class ThreadReport {
    public static ThreadReport REPORT = new ThreadReport();

    private ThreadReport(){
        Monitors.registerObject(this.getClass().getSimpleName(), this);
    }

    @Monitor(name = "runnableCount", type = DataSourceType.GAUGE)
    public int getRunnableThreadCount(){
        Collection<ThreadData> allThreads = CheckThreads.checker.threadDataMap.values();
        Collection<ThreadData> threads = Operations.getByThreadState(allThreads, Thread.State.RUNNABLE);
        return threads.size();
    }

    @Monitor(name = "blockedCount", type = DataSourceType.GAUGE)
    public int getBlockedThreadCount(){
        Collection<ThreadData> allThreads = CheckThreads.checker.threadDataMap.values();
        Collection<ThreadData> threads = Operations.getByThreadState(allThreads, Thread.State.BLOCKED);
        return threads.size();

    }

    @Monitor(name = "waitingCount", type = DataSourceType.GAUGE)
    public int getWaitingThreadCount(){
        Collection<ThreadData> allThreads = CheckThreads.checker.threadDataMap.values();
        Collection<ThreadData> threads = Operations.getByThreadState(allThreads, Thread.State.WAITING);
        return threads.size();

    }

    @Monitor(name = "timedWaitingCount", type = DataSourceType.GAUGE)
    public int getTimedWaitingThreadCount(){
        Collection<ThreadData> allThreads = CheckThreads.checker.threadDataMap.values();
        Collection<ThreadData> threads = Operations.getByThreadState(allThreads, Thread.State.TIMED_WAITING);
        return threads.size();
    }

    @Monitor(name = "processTimePerSecond", type = DataSourceType.GAUGE)
    public long getProcessTimePerSecond(){
        return CheckThreads.checker.currentThreadDumpData.processCpuTimePerSecond;
    }

    @Monitor(name = "processUserTimePerSecond", type = DataSourceType.GAUGE)
    public long getProcessUserTimePerSecond(){
        return CheckThreads.checker.currentThreadDumpData.processUserTimePerSecond;
    }

    HashMap<String, ThreadGroupReport> mapThreadGroupReports = new HashMap<String, ThreadGroupReport>();

    public List<ThreadGroupReport> getThreadGroupReportData(){
        ArrayList<ThreadGroupReport> tgrd = new ArrayList<ThreadGroupReport>();
        Collection<ThreadGroupData> activeGroups = CheckThreads.checker.getActiveThreadGroups();
        for(ThreadGroupData group : activeGroups){
            ThreadGroupReport tgr = mapThreadGroupReports.get(group.threadGroupName);
            if(tgr == null){
                tgr = new ThreadGroupReport(group);
                mapThreadGroupReports.put(group.threadGroupName,tgr);
            }
            tgr.data = group;
            tgrd.add(tgr);
        }
        Collections.sort(tgrd);
        return tgrd;

    }

}
