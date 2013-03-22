package com.netflix.zuul;

/**
 * Stash of helper methods to build consistent log/counter/tracer/epic messages.
 *
 * @author mhawthorne
 */
public class Logging {

    public static final String COUNTER_PREFIX = "ZUUL";
    private Logging() {}

    public static final String buildCounter(String detail) {
        return String.format("%s:%s", COUNTER_PREFIX, detail);
    }

    public static final String buildRoutingOverrideCounter(String detail) {
        return buildCounter(String.format("routing-override:%s", detail));
    }

}
