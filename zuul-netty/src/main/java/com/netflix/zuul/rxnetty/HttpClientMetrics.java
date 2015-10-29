package com.netflix.zuul.rxnetty;

import com.netflix.spectator.api.Counter;
import io.reactivex.netty.spectator.http.HttpClientListener;

import java.util.concurrent.atomic.AtomicInteger;

import static io.reactivex.netty.spectator.SpectatorUtils.*;

public class HttpClientMetrics extends HttpClientListener {

    private final AtomicInteger hostsInPool;
    private final Counter quarantinedHosts;
    private final Counter removedHosts;

    public HttpClientMetrics(String monitorId) {
        super(monitorId);
        hostsInPool = newGauge("requestBacklog", monitorId, new AtomicInteger());
        quarantinedHosts = newCounter("quarantinedHosts", monitorId);
        removedHosts = newCounter("removedHosts", monitorId);
    }

    public void onNewHost() {
        hostsInPool.decrementAndGet();
    }

    public void onHostQuarantine() {
        quarantinedHosts.increment();
        hostsInPool.decrementAndGet();
    }

    public void onHostRemoved() {
        removedHosts.increment();
        hostsInPool.decrementAndGet();
    }

    public int getHostsInPool() {
        return hostsInPool.intValue();
    }

    public long getQuarantinedHosts() {
        return quarantinedHosts.count();
    }

    public long getRemovedHosts() {
        return removedHosts.count();
    }
}
