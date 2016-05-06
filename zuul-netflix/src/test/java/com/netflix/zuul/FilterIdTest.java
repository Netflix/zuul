package com.netflix.zuul;

import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FilterIdTest {
    String zuulAppName = ZuulApplicationInfo.getApplicationName();

    @After
    public void setZuulAppNameBack() {
        ZuulApplicationInfo.setApplicationName(zuulAppName);
    }

    @Test
    public void filterId() {
        FilterId filterId = new FilterId.Builder().applicationName("app")
                .filterType("com.acme.None")
                .filterName("none")
                .build();
        assertThat(filterId.toString(), is("app:none:com.acme.None"));
    }

    @Test
    public void defaultApplicationName() {
        String applicationName = UUID.randomUUID().toString();
        ZuulApplicationInfo.setApplicationName(applicationName);

        FilterId filterId = new FilterId.Builder().filterType("com.acme.None")
                .filterName("none")
                .build();
        assertThat(filterId.toString(), is(applicationName + ":none:com.acme.None"));
    }
}

