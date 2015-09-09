package com.netflix.zuul.rxnetty.origin;

import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A retry function to be applied to an {@link HttpClientRequest} to retry when there are no servers available.
 */
public class RetryWhenNoServersAvailable implements Func1<Observable<? extends Throwable>, Observable<?>> {

    private static final Logger logger = LoggerFactory.getLogger(RetryWhenNoServersAvailable.class);
    private final AtomicInteger consecutiveErrCount = new AtomicInteger();
    private final int maxErrorCount;
    private final long initialBackoffDuration;
    private final TimeUnit timeUnit;
    private final Scheduler backoffScheduler;

    public RetryWhenNoServersAvailable() {
        this(3);
    }

    public RetryWhenNoServersAvailable(int maxErrorCount) {
        this(maxErrorCount, 10, TimeUnit.SECONDS);
    }

    public RetryWhenNoServersAvailable(int maxErrorCount, long initialBackoffDuration, TimeUnit timeUnit) {
        this(maxErrorCount, initialBackoffDuration, timeUnit, Schedulers.computation());
    }

    public RetryWhenNoServersAvailable(int maxErrorCount, long initialBackoffDuration, TimeUnit timeUnit,
                                       Scheduler backoffScheduler) {
        this.maxErrorCount = maxErrorCount;
        this.initialBackoffDuration = initialBackoffDuration;
        this.timeUnit = timeUnit;
        this.backoffScheduler = backoffScheduler;
    }

    @Override
    public Observable<?> call(Observable<? extends Throwable> errStream) {
        return errStream.flatMap(err -> {
            if (consecutiveErrCount.incrementAndGet() < maxErrorCount && err instanceof NoSuchElementException) {
                logger.info("No hosts available, retrying after %d seconds.", initialBackoffDuration);
                //TODO: Have an exponential backoff policy
                return Observable.timer(initialBackoffDuration, timeUnit, backoffScheduler);
            }
            return Observable.error(err);
        });
    }
}

