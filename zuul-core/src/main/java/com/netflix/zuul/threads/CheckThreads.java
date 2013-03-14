package com.netflix.zuul.threads;


import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/10/12
 * Time: 10:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class CheckThreads {

    public LinkedHashMap<Long, ThreadDumpData> threadHistory = new LinkedHashMap<Long, ThreadDumpData>() {
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, ThreadDumpData> kvEntry) {
            if (this.size() > 30) return true;
            return false;
        }
    };

    long lastRunTime = 0;


    public HashMap<Long, ThreadData> threadDataMap = new HashMap<Long, ThreadData>();
    public Map<String, ThreadGroupData> threadGroupDataMap = new HashMap<String, ThreadGroupData>();


    class byValue implements Comparator<String> {
        Map<String, StackInfoData> map;

        public byValue() {

        }

        public byValue(Map<String, StackInfoData> map) {
            this.map = map;
        }

        @Override
        public int compare(String t, String t1) {
            StackInfoData l = map.get(t);
            StackInfoData l1 = map.get(t1);
            return (int)(l1.totalTime - (l.totalTime));
        }
    }

    public Map<String, StackInfoData> stackTimeMap;
    public TreeMap<String, StackInfoData> sortedStackTimeMap;

    /*
   metrics:
   total delta cpu time per interval
   cpu time per second metric
   cpu time per second per thread
   cpu time per second per threadgroup
   synchronized time
    */

    public static CheckThreads getChecker() {
        return checker;
    }

    public static CheckThreads checker = new CheckThreads();
    static DynamicIntProperty checkThreadSleepTime = DynamicPropertyFactory.getInstance().getIntProperty("hackday.threaddump.sleeptime.ms", 10000);
    static Thread run;
    ThreadDumpData currentThreadDumpData = null;

    public CheckThreads() {
        stackTimeMap = new HashMap<String, StackInfoData>();

    }

    public static void run() {
        run = new Thread() {
            public void run() {
                while (true) {
                    checker.checkThreads();
                    try {
                        sleep(checkThreadSleepTime.get());
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            }
        };
        run.start();
    }


    public void checkThreads() {

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        ThreadInfo[] threads = threadBean.dumpAllThreads(true, true);
        long currentTime = System.currentTimeMillis();

        long deltaTime = ((currentTime - lastRunTime) / 1000l);

        ThreadDumpData tdd = new ThreadDumpData(threads, deltaTime);
        threadHistory.put(currentTime, tdd);

        HashMap<String, List<ThreadData>> groupMap = calculateThreadDumpStats(tdd, currentThreadDumpData, threadDataMap, deltaTime);
        currentThreadDumpData = tdd;
        lastRunTime = currentTime;

        threadGroupDataMap = calculateThreadGroupData(groupMap, deltaTime, threadGroupDataMap, tdd);


        ThreadReport.REPORT.getThreadGroupReportData();
        getStackTraceGroupTimes();

    }

    public static Map<String, ThreadGroupData> calculateThreadGroupData(HashMap<String, List<ThreadData>> groupMap,
                                                                        long deltaTime,
                                                                        Map<String, ThreadGroupData> threadGroupDataMap,
                                                                        ThreadDumpData tdd) {

        Map<String, ThreadGroupData> currentThreadGroupDataMap = new HashMap<String, ThreadGroupData>();
        Map<String, ThreadGroupData> historyThreadGroupDataMap = new HashMap<String, ThreadGroupData>();

        for(String threadGroupname: groupMap.keySet()){

            List<ThreadData> groupThreads = groupMap.get(threadGroupname);
            ThreadGroupData tgd = threadGroupDataMap.get(threadGroupname);
            if (tgd == null){
                tgd = new ThreadGroupData();
            }
            currentThreadGroupDataMap.put(threadGroupname, tgd);
            tgd.calculateThreadGroupTimes(threadGroupname, groupThreads, deltaTime);
            ThreadGroupData historicalTgg = new ThreadGroupData();
            historicalTgg.threadTimePerSecond = tgd.threadTimePerSecond;
            historicalTgg.threadUserTimePerSecond= tgd.threadUserTimePerSecond;
            historicalTgg.threadGroupName = tgd.threadGroupName;
            historicalTgg.blockedCountPerSecond = tgd.blockedCountPerSecond;
            historicalTgg.blockedTimePerSecond = tgd.blockedTimePerSecond;
            historicalTgg.waitCountPerSecond = tgd.waitCountPerSecond;
            historicalTgg.waitTimePerSecond = tgd.waitTimePerSecond;
            historicalTgg.groupThreads = tgd.groupThreads;
            historyThreadGroupDataMap.put(threadGroupname,historicalTgg);
        }
        tdd.threadGroupDataMap = historyThreadGroupDataMap;
        return currentThreadGroupDataMap;
    }

    public static HashMap<String, List<ThreadData>> calculateThreadDumpStats(ThreadDumpData tdd,
                                                                             ThreadDumpData lasttdd,
                                                                             HashMap<Long, ThreadData> threadDataMap,
                                                                             long deltaTime) {
        HashMap<Long, ThreadData> dataMap = new HashMap<Long, ThreadData>();
        HashMap<String, List<ThreadData>> groupMap = new HashMap<String, List<ThreadData>>();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long lThreadTime = 0;
        long lThreadUserTime = 0;

        if (tdd.groupMap == null) {
            for(ThreadInfo thread: tdd.dump){
                ThreadData tdata = calculateDeltaTimes(thread, deltaTime, threadDataMap);
                lThreadTime += threadBean.getThreadCpuTime(thread.getThreadId());
                lThreadUserTime += threadBean.getThreadUserTime(thread.getThreadId());
                ThreadData threadData = dataMap.get(tdata.getThreadId());
                if (threadData == null) {
                    dataMap.put(tdata.getThreadId(), tdata);
                }

                String threadname = thread.getThreadName();
                String groupname = getThreadGroupName(threadname);

                List<ThreadData> groupThreads = groupMap.get(groupname);
                if (groupThreads == null) {
                    groupThreads = new ArrayList<ThreadData>();
                    groupMap.put(groupname,groupThreads);
                }
                groupThreads.add(tdata);

            }
            tdd.groupMap= groupMap;
            if (lasttdd != null) {
                long lDeltaCpuTime = lThreadTime - lasttdd.processCpuTime;
                long lDeltaUserCpuTime = lThreadUserTime - lasttdd.processUserCpuTime;
                tdd.processCpuTimePerSecond = lDeltaCpuTime / deltaTime / 1000000l;
                tdd.processUserTimePerSecond = lDeltaUserCpuTime / deltaTime / 1000000l;
            }
            tdd.processCpuTime = lThreadTime;
            tdd.processUserCpuTime = lThreadUserTime;

        }

        return tdd.groupMap;
    }

    public static String getThreadGroupName(String threadname) {
        String cleanThreadName = "";

        for(int i = 0; i < threadname.length(); ++i ){
            char c = threadname.charAt(i);
            if (!Character.isDigit(c)) {
                cleanThreadName += c;
            }
        }
        threadname = cleanThreadName;

        if (threadname.lastIndexOf("-") != -1) {
            threadname = threadname.substring(0, threadname.lastIndexOf("-"));
        }
        return threadname;
    }




    private Map<String, StackInfoData> getStackTraceGroupTimes() {
        Map<String, List<ThreadData>> stackTraceData = getStackTraceGroups(5);
        if(stackTraceData != null){
        for(String stackTrace: stackTraceData.keySet()){
            long lTime = 0;
            List<ThreadData> data = stackTraceData.get(stackTrace);
            if(data != null){
             for(ThreadData threadData : data){

                lTime += threadData.threadTimePerSecond;
                }
            }
            StackInfoData stackInfoData = stackTimeMap.get(stackTrace);
            if (stackInfoData == null) {
                if (lTime > 0) {
                    stackInfoData = new StackInfoData();
                    stackInfoData.totalTime = lTime;
                    stackInfoData.numberOfDumps++;
                    stackTimeMap.put(stackTrace,stackInfoData);
                }
            } else {
                if (lTime > 0) {
                    stackInfoData.totalTime += lTime;
                    stackInfoData.numberOfDumps++;
                }
            }
        }
        }

        byValue comp = new byValue();
        TreeMap<String, StackInfoData> map = new TreeMap<String, StackInfoData>(comp);
        comp.map = stackTimeMap;
        map.putAll(stackTimeMap);
        sortedStackTimeMap = map;
        return map;
    }

    public Map<String, List<ThreadData>> getStackTraceGroups(int stackSize) {
        Collection<ThreadData> allThreads = threadDataMap.values();
        return getStackTraceGroups(stackSize, allThreads);
    }

    public Map<String, List<ThreadData>> getStackTraceGroups(int stackSize, Collection<ThreadData> allThreads) {

        allThreads = Operations.getNonZeroMetricsThreadData(allThreads);

        Map<String, List<ThreadData>> map = Operations.groupByStackTraceElements(stackSize, allThreads);

        return map;
    }

    public Collection<ThreadGroupData> getActiveThreadGroups() {
        Collection<ThreadGroupData> allGroups = threadGroupDataMap.values();
        return getActiveThreadGroups(allGroups);
    }

    public static Collection<ThreadGroupData> getActiveThreadGroups(Collection<ThreadGroupData> allGroups) {
        List<ThreadGroupData> activeGroups = Operations.getNonZeroMetricsThreadGroupData(allGroups);
         Collections.sort(activeGroups);
        return activeGroups;

    }

    public Collection<ThreadData> getActiveThreads() {
        Collection<ThreadData> allThreads = (Collection<ThreadData>) threadDataMap.values();
        return getActiveThreads(allThreads);
    }

    public static Collection<ThreadData> getActiveThreads(Collection<ThreadData> allThreads) {
        List<ThreadData> activeThreads = Operations.getNonZeroMetricsThreadData(allThreads);
        List<ThreadData> threads =  activeThreads;
        Collections.sort(threads);
        return threads;
    }

    public static ThreadData calculateDeltaTimes(ThreadInfo threadInfo, long deltaTime, Map<Long, ThreadData> threadDataMap) {
        ThreadData threadData = threadDataMap.get(threadInfo.getThreadId());
        if (threadData == null) {
            threadData = new ThreadData();
            threadDataMap.put(threadInfo.getThreadId(), threadData);

        } else {

        }
        threadData.addThreadInfo(threadInfo, deltaTime);
        return threadData;
    }

}
