package com.netflix.zuul.rxnetty;

import com.netflix.numerus.NumerusProperty;
import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingNumberEvent;
import com.netflix.numerus.NumerusRollingPercentile;
import netflix.ocelli.rxnetty.FailureListener;
import netflix.ocelli.rxnetty.protocol.http.WeightedHttpClientListener;

import java.util.concurrent.TimeUnit;

import static com.netflix.numerus.NumerusProperty.Factory.asProperty;

public class HttpClientListenerImpl extends WeightedHttpClientListener {

    private final HostMetrics hostMetrics = new HostMetrics();
    private final FailureListener failureListener;
    private final HttpClientMetrics clientMetrics;

    public HttpClientListenerImpl(FailureListener failureListener, HttpClientMetrics clientMetrics) {
        this.failureListener = failureListener;
        this.clientMetrics = clientMetrics;
        clientMetrics.onNewHost();
    }

    @Override
    public int getWeight() {
        /*Higher 95 percentile latency, lower weight*/
        return Integer.MAX_VALUE - hostMetrics.getRollingLatencyPercentile().getPercentile(95);
    }

    @Override
    public void onRequestWriteComplete(long duration, TimeUnit timeUnit) {
        hostMetrics.getRollingCounter().add(HostMetrics.EventType.PENDING_REQUESTS, 1);
    }

    @Override
    public void onResponseReceiveComplete(long duration, TimeUnit timeUnit) {
        hostMetrics.getRollingCounter().add(HostMetrics.EventType.PENDING_REQUESTS, -1);
    }

    @Override
    public void onResponseHeadersReceived(int responseCode, long duration, TimeUnit timeUnit) {
        if (responseCode == 503) {
            hostMetrics.getRollingCounter().add(HostMetrics.EventType.SERVICE_UNAVAILABLE, 1);
            long unavailableInCurrentWindow =
                    hostMetrics.getRollingCounter().getValueOfLatestBucket(HostMetrics.EventType.SERVICE_UNAVAILABLE);
            if (unavailableInCurrentWindow > 3) {
                /*If we receive more than 3 throttled responses, then quarantine host*/
                failureListener.quarantine(1, TimeUnit.MINUTES);
                clientMetrics.onHostQuarantine();
            }
        }
        hostMetrics.getRollingLatencyPercentile().addValue((int) duration);
    }

    @Override
    public void onConnectFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        hostMetrics.getRollingCounter().add(HostMetrics.EventType.CONNECT_FAILURES, 1);
        long connectFailedInCurrentWindow =
                hostMetrics.getRollingCounter().getValueOfLatestBucket(HostMetrics.EventType.CONNECT_FAILURES);
        if (connectFailedInCurrentWindow > 2) {
            /*If more than two connect failed in the current window, then consider host unavailable.*/
            failureListener.remove();
            clientMetrics.onHostRemoved();
        }
    }

    public static final class HostMetrics {

        private static final NumerusProperty<Integer> latency_timeInMilliseconds = asProperty(60000);
        private static final NumerusProperty<Integer> latency_numberOfBuckets = asProperty(12); // 12 buckets at 5000ms each
        private static final NumerusProperty<Integer> latency_bucketDataLength = asProperty(1000);
        private static final NumerusProperty<Boolean> latency_enabled = asProperty(true);

        private static final NumerusProperty<Integer> count_timeInMilliseconds = asProperty(10000);
        private static final NumerusProperty<Integer> count_numberOfBuckets = asProperty(10); // 11 buckets at 1000ms each

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

}
