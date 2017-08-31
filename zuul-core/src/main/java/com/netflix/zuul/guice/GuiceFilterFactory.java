package com.netflix.zuul.guice;

import com.google.inject.Injector;
import com.netflix.zuul.FilterFactory;
import com.netflix.zuul.filters.ZuulFilter;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GuiceFilterFactory implements FilterFactory {

    private final Injector injector;

    @Inject
    public GuiceFilterFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ZuulFilter newInstance(Class clazz) throws Exception {
        return (ZuulFilter) injector.getInstance(clazz);
    }
}
