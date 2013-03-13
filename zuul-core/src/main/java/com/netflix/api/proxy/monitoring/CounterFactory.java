package com.netflix.api.proxy.monitoring;

/**
 * Abstraction layer to provide counter based monitoring.
 *
 * @author mhawthorne
 */
public abstract class CounterFactory {

    private static CounterFactory INSTANCE;

    public static final void initialize(CounterFactory f) {
        INSTANCE = f;
    }

    public static final CounterFactory instance() {
        if(INSTANCE == null) throw new IllegalStateException(String.format("%s not initialized", CounterFactory.class.getSimpleName()));
        return INSTANCE;
    }

    public abstract void increment(String name);

}
