package com.netflix.api.proxy.monitoring;

/**
 * @author mhawthorne
 */
public class MonitoringHelper {

    public static final void mockForTests() {
        CounterFactory.initialize(new CounterFactoryImpl());
        TracerFactory.initialize(new TracerFactoryImpl());
    }

    private static final class CounterFactoryImpl extends CounterFactory {
        @Override
        public void increment(String name) {}
    }

    private static final class TracerFactoryImpl extends TracerFactory {
        @Override
        public Tracer startMicroTracer(String name) {
            return new TracerImpl();
        }
    }

    private static final class TracerImpl implements Tracer {
        @Override
        public void setName(String name) {}

        @Override
        public void stopAndLog() {}
    }

}
