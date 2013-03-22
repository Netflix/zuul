package com.netflix.zuul.stats.monitoring;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 3/18/13
 * Time: 4:33 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Monitor {
    void register(NamedCount monitorObj);
}
