package com.netflix.api.proxy.threads;

import java.lang.management.ThreadInfo;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/20/12
 * Time: 3:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class Operations {



    public static List<ThreadGroupData> getNonZeroMetricsThreadGroupData(Collection<ThreadGroupData> inMetrics) {
        List<ThreadGroupData> list = new ArrayList<ThreadGroupData>();
        for(ThreadGroupData metric : inMetrics ){
            if(metric.blockedCountPerSecond > 0 ||
                    metric.blockedTimePerSecond > 0 ||
                    metric.threadTimePerSecond > 0 ||
                    metric.threadUserTimePerSecond > 0 ||
                    metric.waitCountPerSecond > 0 ||
                    metric.waitTimePerSecond > 0){
                list.add(metric);
            }
        }
        return list;

    }

    public static List<ThreadData> getNonZeroMetricsThreadData(Collection<ThreadData> inMetrics) {
        List<ThreadData> list = new ArrayList<ThreadData>();
        for(ThreadData metric : inMetrics ){
            if(metric.blockedCountPerSecond > 0 ||
                    metric.blockedTimePerSecond > 0 ||
                    metric.threadTimePerSecond > 0 ||
                    metric.threadUserTimePerSecond > 0 ||
                    metric.waitCountPerSecond > 0 ||
                    metric.waitTimePerSecond > 0){
                list.add(metric);
            }
        }
        return list;

    }

    public static List<ThreadMetrics> getNonZeroMetrics(Collection<ThreadMetrics> inMetrics) {

        List<ThreadMetrics> list = new ArrayList<ThreadMetrics>();
        for(ThreadMetrics metric : inMetrics ){
            if(metric.blockedCountPerSecond > 0 ||
                    metric.blockedTimePerSecond > 0 ||
                    metric.threadTimePerSecond > 0 ||
                    metric.threadUserTimePerSecond > 0 ||
                    metric.waitCountPerSecond > 0 ||
                    metric.waitTimePerSecond > 0){
                list.add(metric);
            }
        }
        return list;
    }

    public static Map<Long, ThreadData> toThreadDataMap(ThreadInfo[] threads) {
        Map<Long,ThreadData> map = new HashMap<Long, ThreadData>();
        for(ThreadInfo threadInfo : threads){
            ThreadData data = new ThreadData();
            data.addThreadInfo(threadInfo, 0);
            map.put(threadInfo.getThreadId(), data);
        }
        return map;
    }



    public static List<ThreadData> getByThreadState(Collection<ThreadData> threads, Thread.State state) {

        List<ThreadData> list = new ArrayList<ThreadData>();
        for(ThreadData thread: threads){
            if(thread.getThreadState().equals(state)){
                list.add(thread);
            }
        }
        if (list == null) return Collections.emptyList();
        return list;
    }


    public static Map<String, List<ThreadData>> groupByStackTraceElements(int numberOfLines, Collection<ThreadData> threads) {
        HashMap<String, List<ThreadData>> stackMap = new HashMap<String, List<ThreadData>>();

        for(ThreadData thread: threads){

            int nIndex = 0;

            StackTraceElement[] stackTrace = thread.getStackTrace();
            for (int h = 0; h < stackTrace.length; ++h) {
                String stackTraceGroup = "";
                for (int i = 0; i < numberOfLines; ++i) {
                    int lookupIndex = nIndex + i;
                    if (lookupIndex < stackTrace.length) {
                        stackTraceGroup += stackTrace[lookupIndex].toString() + "<br>";
                    }
                }
                List<ThreadData> group = stackMap.get(stackTraceGroup);
                if (group == null) {
                    group = new ArrayList<ThreadData>();
                    stackMap.put(stackTraceGroup, group);
                }
                if (!group.contains(thread)) {
                    group.add(thread);
                }

                nIndex++;
            }
        }

        return stackMap;
        /*
        HashMap<String, List<ThreadData>> returnMap = new HashMap<String, List<ThreadData>>();
        stackMap.keySet().each {key ->
            if (stackMap[key].size() > 1) {
                returnMap[key] = stackMap[key]
            }
        }
        return returnMap;
        */
    }


}
