package com.netflix.zuul.rxnetty;

import com.netflix.spectator.api.Counter;
import io.reactivex.netty.spectator.http.HttpClientListener;

import java.util.concurrent.atomic.AtomicInteger;

import static io.reactivex.netty.spectator.SpectatorUtils.*;

public class HttpClientMetrics extends HttpClientListener {

    private final AtomicInteger hostsInPool;
    private final Counter foundUnusableHosts;
    private final Counter noUsableHosts;

    public HttpClientMetrics(String monitorId) {
        super(monitorId);
        hostsInPool = newGauge("hostsInPool", monitorId, new AtomicInteger());
        foundUnusableHosts = newCounter("foundUnusableHosts", monitorId);
        noUsableHosts = newCounter("noUnusableHosts", monitorId);
    }

    public void setHostsInPool(int hostsInPool) {
        this.hostsInPool.set(hostsInPool);
    }

    public void foundTwoUnusableHosts() {
        this.foundUnusableHosts.increment();
    }

    public void noUsableHostsFound() {
        this.noUsableHosts.increment();
    }

    public AtomicInteger getHostsInPool() {
        return hostsInPool;
    }

    public Counter getFoundUnusableHosts() {
        return foundUnusableHosts;
    }

    public Counter getNoUsableHosts() {
        return noUsableHosts;
    }
}
