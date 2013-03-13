package com.netflix.api.proxy.threads;

import java.lang.management.ThreadInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 9/14/12
 * Time: 9:47 AM
 * To change this template use File | Settings | File Templates.
 */
public class ThreadDumpData {
    long deltaTimeFromLastDump;
    ThreadInfo[] dump;
    long processCpuTime = -1;
    long processUserCpuTime = 0;

    long processCpuTimePerSecond = 0;
    long processUserTimePerSecond = 0;
    public HashMap<String, List<ThreadData>> groupMap = null;
    public Map<String, ThreadGroupData> threadGroupDataMap;


    public ThreadDumpData(ThreadInfo[] threads, long delta){
        dump = threads;
        deltaTimeFromLastDump = delta;
    }

    public long getDeltaTimeFromLastDump() {
        return deltaTimeFromLastDump;
    }

    public ThreadInfo[] getDump() {
        return dump;
    }

    public long getProcessCpuTime() {
        return processCpuTime;
    }

    public long getProcessUserCpuTime() {
        return processUserCpuTime;
    }

    public long getProcessCpuTimePerSecond() {
        return processCpuTimePerSecond;
    }

    public long getProcessUserTimePerSecond() {
        return processUserTimePerSecond;
    }

    public HashMap<String, List<ThreadData>> getGroupMap() {
        return groupMap;
    }

    public Map<String, ThreadGroupData> getThreadGroupDataMap() {
        return threadGroupDataMap;
    }
}
