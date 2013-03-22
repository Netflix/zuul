package com.netflix.zuul.stats.monitoring;

import com.netflix.zuul.stats.ErrorStatsData;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 3/18/13
 * Time: 4:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class MonitorRegistry {

    private static MonitorRegistry instance;

    private Monitor publisher;

    public static MonitorRegistry getInstance() {
        return instance;
    }

    public void registerObject(NamedCount monitorObj) {
      if(publisher != null) publisher.register(monitorObj);
    }
}
