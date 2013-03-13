package com.netflix.api.proxy.monitoring;

/**
 * Time based monitoring metric.
 *
 * @author mhawthorne
 */
public interface Tracer {

    void stopAndLog();

    void setName(String name);

}
