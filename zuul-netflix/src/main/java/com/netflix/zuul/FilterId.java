package com.netflix.zuul;

import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;

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
}
