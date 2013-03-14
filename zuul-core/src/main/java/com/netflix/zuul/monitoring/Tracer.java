package com.netflix.zuul.monitoring;

/**
 * Time based monitoring metric.
 *
 * @author mhawthorne
 */
public interface Tracer {

    void stopAndLog();

    void setName(String name);

}
