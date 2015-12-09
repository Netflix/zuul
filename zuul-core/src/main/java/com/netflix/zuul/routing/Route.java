package com.netflix.zuul.routing;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by chrisp on 6/9/15.
 */
public class Route {

    private final String vip;
    private final boolean fallback;
    private final boolean dryRun;

    public Route(String vip) {
        this(vip, false, false);
    }

    public Route(String vip, boolean fallback) {
        this(vip, fallback, false);
    }

    public Route(String vip, boolean fallback, boolean dryRun) {
        this.fallback = fallback;
        checkArgument(vip != null && vip.trim().length() > 0);
        this.vip = vip;
        this.dryRun = dryRun;
    }

    public String getVip() {
        return vip;
    }


    public boolean shouldFallback() {
        return fallback;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public static Route asDryRun(Route route) {
        return new Route(route.vip, route.fallback, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Route route = (Route) o;

        if (fallback != route.fallback) return false;
        if (dryRun != route.dryRun) return false;
        return vip.equals(route.vip);

    }

    @Override
    public int hashCode() {
        int result = vip.hashCode();
        result = 31 * result + (fallback ? 1 : 0);
        result = 31 * result + (dryRun ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Route{" +
                "vip='" + vip + '\'' +
                ", fallback=" + fallback +
                ", dryRun=" + dryRun +
                '}';
    }
}
