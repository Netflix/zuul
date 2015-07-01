package com.netflix.zuul;

import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Immutable
@ThreadSafe
public final class FilterId {

    private String value;

    private FilterId(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static class Builder {
        private String applicationName = ZuulApplicationInfo.getApplicationName();
        private String filterType;
        private String filterName;

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder filterType(String filterType) {
            this.filterType = filterType;
            return this;
        }

        public Builder filterName(String filterName) {
            this.filterName = filterName;
            return this;
        }

        public FilterId build() {
            return new FilterId(applicationName + ":" + filterName + ":" + filterType);
        }
    }


    public static class UnitTest {

        String zuulAppName = ZuulApplicationInfo.getApplicationName();

        @After
        public void setZuulAppNameBack() {
            ZuulApplicationInfo.setApplicationName(zuulAppName);
        }

        @Test
        public void filterId() {
            FilterId filterId = new Builder().applicationName("app")
                                             .filterType("com.acme.None")
                                             .filterName("none")
                                             .build();
            assertThat(filterId.toString(), is("app:none:com.acme.None"));
        }

        @Test
        public void defaultApplicationName() {
            String applicationName = UUID.randomUUID().toString();
            ZuulApplicationInfo.setApplicationName(applicationName);

            FilterId filterId = new Builder().filterType("com.acme.None")
                                             .filterName("none")
                                             .build();
            assertThat(filterId.toString(), is(applicationName + ":none:com.acme.None"));
        }
    }
}
