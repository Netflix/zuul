package com.netflix.zuul.netty.connectionpool;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.zuul.origins.OriginName;

/**
 * @author Justin Guerra
 * @since 2/26/25
 */
public record ConnectionPoolMetrics(Counter createNewConnCounter,
                                    Counter createConnSucceededCounter,
                                    Counter createConnFailedCounter,
                                    Counter closeConnCounter,
                                    Counter closeAbovePoolHighWaterMarkCounter,
                                    Counter closeExpiredConnLifetimeCounter,
                                    Counter requestConnCounter,
                                    Counter reuseConnCounter,
                                    Counter releaseConnCounter,
                                    Counter alreadyClosedCounter,
                                    Counter connTakenFromPoolIsNotOpen,
                                    Counter maxConnsPerHostExceededCounter,
                                    Counter closeWrtBusyConnCounter,
                                    Counter circuitBreakerClose,
                                    PercentileTimer connEstablishTimer) {

    public static final String METRIC_PREFIX = "connectionpool_";


    public static ConnectionPoolMetrics create(OriginName originName, Registry registry) {
        Counter createNewConnCounter = newCounter("create", originName, registry);
        Counter createConnSucceededCounter = newCounter("create_success", originName, registry);
        Counter createConnFailedCounter = newCounter("create_fail", originName, registry);

        Counter closeConnCounter = newCounter("close", originName, registry);
        Counter closeAbovePoolHighWaterMarkCounter = newCounter("closeAbovePoolHighWaterMark", originName, registry);
        Counter closeExpiredConnLifetimeCounter = newCounter("closeExpiredConnLifetime", originName, registry);
        Counter requestConnCounter = newCounter("request", originName, registry);
        Counter reuseConnCounter = newCounter("reuse", originName, registry);
        Counter releaseConnCounter = newCounter("release", originName, registry);
        Counter alreadyClosedCounter = newCounter("alreadyClosed", originName, registry);
        Counter connTakenFromPoolIsNotOpen = newCounter("fromPoolIsClosed", originName, registry);
        Counter maxConnsPerHostExceededCounter = newCounter("maxConnsPerHostExceeded", originName, registry);
        Counter closeWrtBusyConnCounter = newCounter("closeWrtBusyConnCounter", originName, registry);
        Counter circuitBreakerClose = newCounter("closeCircuitBreaker", originName, registry);

        PercentileTimer connEstablishTimer = PercentileTimer.get(
                registry, registry.createId(METRIC_PREFIX + "createTiming", "id", originName.getMetricId()));
        return new ConnectionPoolMetrics(createNewConnCounter, createConnSucceededCounter, createConnFailedCounter,
                closeConnCounter, closeAbovePoolHighWaterMarkCounter, closeExpiredConnLifetimeCounter, requestConnCounter,
                reuseConnCounter, releaseConnCounter, alreadyClosedCounter, connTakenFromPoolIsNotOpen,
                maxConnsPerHostExceededCounter, closeWrtBusyConnCounter, circuitBreakerClose, connEstablishTimer);
    }

    private static Counter newCounter(String metricName, OriginName originName, Registry registry) {
        return registry.counter(METRIC_PREFIX + metricName, "id", originName.getMetricId());
    }

}
