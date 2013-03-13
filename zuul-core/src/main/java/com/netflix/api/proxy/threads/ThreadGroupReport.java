package com.netflix.api.proxy.threads;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 8/23/12
 * Time: 11:16 AM
 * To change this template use File | Settings | File Templates.
 */
public class ThreadGroupReport implements Comparable<ThreadGroupReport> {
    ThreadGroupData data;

    String name;

    public ThreadGroupReport(ThreadGroupData tgd){
        data= tgd;
        name = tgd.threadGroupName;
        Monitors.registerObject(name, this);
    }


    public String getThreadGroupName(){
        return data.threadGroupName;
    }

    /*
    long waitTimePerSecond;
    long waitCountPerSecond;
    long blockedTimePerSecond;
    long blockedCountPerSecond;
    long threadTimePerSecond = 0;
    long threadUserTimePerSecond = 0;
     */

    @Monitor(name = "blockedTimePerSecond", type = DataSourceType.GAUGE)
    public long getBlockedTimePerSecond(){
        return data.blockedTimePerSecond;
    }

    @Monitor(name = "blockedCountPerSecond", type = DataSourceType.GAUGE)
    public long getBlockedCountPerSecond(){
        return data.blockedCountPerSecond;
    }

    @Monitor(name = "waitTimePerSecond", type = DataSourceType.GAUGE)
    public long getWaitTimePerSecond(){
        return data.waitTimePerSecond;
    }

    @Monitor(name = "waitCountPerSecond", type = DataSourceType.GAUGE)
    public long getWaitCountPerSecond(){
        return data.waitCountPerSecond;
    }

    @Monitor(name = "threadTimePerSecond", type = DataSourceType.GAUGE)
    public long getThreadTimePerSecond(){
        return data.threadTimePerSecond;
    }

    @Monitor(name = "threadUserTimePerSecond", type = DataSourceType.GAUGE)
    public long getThreadUserTimePerSecond(){
        return data.threadUserTimePerSecond;
    }



    @Override
    public int compareTo(ThreadGroupReport o) {
        return (int) (data.blockedTimePerSecond - o.data.blockedTimePerSecond);
    }

    public ThreadGroupData getData() {
        return data;
    }

    public String getName() {
        return name;
    }
}
