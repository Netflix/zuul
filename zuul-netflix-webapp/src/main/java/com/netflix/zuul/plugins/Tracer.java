package com.netflix.zuul.plugins;

import com.netflix.zuul.monitoring.TracerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 4/10/13
 * Time: 4:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class Tracer extends TracerFactory {
    @Override
    public com.netflix.zuul.monitoring.Tracer startMicroTracer(String name) {
        return new com.netflix.zuul.monitoring.Tracer() {
            @Override
            public void stopAndLog() {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void setName(String name) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
    }
}
