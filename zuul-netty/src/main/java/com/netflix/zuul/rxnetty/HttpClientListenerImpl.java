package com.netflix.zuul.rxnetty;

import com.netflix.numerus.NumerusProperty;
import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingNumberEvent;
import com.netflix.numerus.NumerusRollingPercentile;
import com.netflix.zuul.rxnetty.HttpClientListenerImpl.HostMetrics.EventType;
import io.reactivex.netty.protocol.http.client.events.HttpClientEventsListener;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static com.netflix.numerus.NumerusProperty.Factory.*;

public class HttpClientListenerImpl extends HttpClientEventsListener {

    private final HostMetrics hostMetrics;

    public HttpClientListenerImpl(HostMetrics hostMetrics) {
        this.hostMetrics = hostMetrics;
    }

    public HttpClientListenerImpl() {
        this(new HostMetrics());
    }

    public int getWeight() {
        if (getConnectFailedInCurrentWindow() > 2) {
            /*If more than two connect failed in the current window, then consider host unavailable.*/
            return Integer.MIN_VALUE;
        }

        int additionCost = 0;
        if (getThrottledRequestsInCurrentWindow() > 3) {
            /*If we receive more than 3 throttled responses, then add an additional cost to lower the probability of
            selection*/
            additionCost = 60000; /*Will be selected in favor of servers that have 95 percentile latency > 1 min*/
        }

        /*Higher 95 percentile latency, lower weight*/
        return Integer.MAX_VALUE - (hostMetrics.getRollingLatencyPercentile().getPercentile(95) + additionCost);
    }

    @Override
    public void onRequestWriteComplete(long duration, TimeUnit timeUnit) {
        hostMetrics.getRollingCounter().add(HostMetrics.EventType.PENDING_REQUESTS, 1);
    }

    @Override
    public void onResponseReceiveComplete(long duration, TimeUnit timeUnit) {
        hostMetrics.getRollingCounter().add(HostMetrics.EventType.PENDING_REQUESTS, -1);
    }

    public boolean isUnusable(int weight) {
        return weight == Integer.MIN_VALUE;
    }

    @Override
    public void onResponseHeadersReceived(int responseCode, long duration, TimeUnit timeUnit) {
        if (responseCode == 503) {
            hostMetrics.getRollingCounter().add(HostMetrics.EventType.SERVICE_UNAVAILABLE, 1);
        }
        hostMetrics.getRollingLatencyPercentile().addValue((int) duration);
    }

    @Override
    public void onConnectFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        hostMetrics.getRollingCounter().add(HostMetrics.EventType.CONNECT_FAILURES, 1);
    }

    protected long getConnectFailedInCurrentWindow() {
        return hostMetrics.getRollingCounter().getValueOfLatestBucket(EventType.CONNECT_FAILURES);
    }

    protected long getThrottledRequestsInCurrentWindow() {
        return hostMetrics.getRollingCounter().getValueOfLatestBucket(EventType.SERVICE_UNAVAILABLE);
    }

    public static class HostMetrics {

        private static final NumerusProperty<Integer> latency_timeInMilliseconds = asProperty(60000);
        private static final NumerusProperty<Integer> latency_numberOfBuckets = asProperty(12);
                // 12 buckets at 5000ms each
        private static final NumerusProperty<Integer> latency_bucketDataLength = asProperty(1000);
        private static final NumerusProperty<Boolean> latency_enabled = asProperty(true);

        private static final NumerusProperty<Integer> count_timeInMilliseconds = asProperty(10000);
        private static final NumerusProperty<Integer> count_numberOfBuckets = asProperty(10);
                // 11 buckets at 1000ms each

        private final NumerusRollingPercentile p = new NumerusRollingPercentile(latency_timeInMilliseconds,
                                                                                latency_numberOfBuckets,
                                                                                latency_bucketDataLength,
                                                                                latency_enabled);
        private final NumerusRollingNumber n = new NumerusRollingNumber(EventType.BOOTSTRAP,
                                                                        count_timeInMilliseconds,
                                                                        count_numberOfBuckets);

        public NumerusRollingPercentile getRollingLatencyPercentile() {
            return p;
        }

        public NumerusRollingNumber getRollingCounter() {
            return n;
        }

        public enum EventType implements NumerusRollingNumberEvent {

            BOOTSTRAP(1), SUCCESS(1), FAILURE(1), SERVICE_UNAVAILABLE(1), PENDING_REQUESTS(1), CONNECT_FAILURES(1);

            private final int type;

            EventType(int type) {
                this.type = type;
            }

            public boolean isCounter() {
                return type == 1;
            }

            public boolean isMaxUpdater() {
                return type == 2;
            }

            @Override
            public EventType[] getValues() {
                return values();
            }

        }
    }


    public static class UnitTest {

        @Test
        public void testWithConnectFailures() {
            HttpClientListenerImpl listener = new HttpClientListenerImpl();

            for (int i =0; i < 4; i++) {
                listener.onConnectFailed(1, TimeUnit.MILLISECONDS, new NullPointerException());
            }

            Assert.assertEquals("Unexpected weight.", Integer.MIN_VALUE, listener.getWeight());
        }

        @Test
        public void testWithThrottle() {
            HttpClientListenerImpl listener = new HttpClientListenerImpl() {
                @Override
                protected long getThrottledRequestsInCurrentWindow() {
                    return 5;
                }
            };

            for (int i =0; i < 5; i++) {
                listener.onResponseHeadersReceived(503, 1, TimeUnit.MILLISECONDS);
            }

            Assert.assertEquals("Unexpected weight.", Integer.MAX_VALUE - 60000, listener.getWeight());
        }
    }
}

