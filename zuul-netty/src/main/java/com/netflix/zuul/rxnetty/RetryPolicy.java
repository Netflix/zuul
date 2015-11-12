package com.netflix.zuul.rxnetty;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Action;
import rx.functions.Action0;
import rx.functions.Actions;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RetryPolicy implements Func1<Observable<? extends Throwable>, Observable<?>> {

    private final int maxRetries;
    private final Func1<Throwable, Boolean> retryCriteria;
    private final Func1<Throwable, Observable<Long>> resumeStreamFunc;
    private final Action0 beforeRetryAction;

    private RetryPolicy(int maxRetries, Func1<Throwable, Boolean> retryCriteria,
                        Func1<Throwable, Observable<Long>> resumeStreamFunc, Action0 beforeRetryAction) {
        this.maxRetries = maxRetries;
        this.retryCriteria = retryCriteria;
        this.resumeStreamFunc = resumeStreamFunc;
        this.beforeRetryAction = beforeRetryAction;
    }

    @Override
    public final Observable<?> call(Observable<? extends Throwable> errStream) {

        final AtomicInteger counts = new AtomicInteger();

        return errStream.flatMap(err -> {
            int attempts = counts.incrementAndGet();
            if (attempts <= maxRetries && retryCriteria.call(err)) {
                beforeRetryAction.call();
                return resumeStreamFunc.call(err);
            } else {
                return Observable.error(err);
            }
        });
    }

    /**
     * Creates a new {@code RetryPolicy} using the supplied function to decide on every error whether the source should
     * be retried.
     *
     * @param retryCriteria Function to determine whether to retry for every error. This function will never be called
     * concurrently and will always be called by the same thread.
     *
     * @return A new {@code RetryPolicy} instance.
     */
    public static RetryPolicy createSingleRetry(Func1<Throwable, Boolean> retryCriteria) {
        return new RetryPolicy(1, retryCriteria, throwable -> Observable.just(1L)/*Retry immediately*/, Actions.empty());
    }

    /**
     * Appends a new criteria to the existing retry criteria of this policy. Retry will be performed if any of the
     * criterion pass for an error.
     *
     * @param retryCriteria Retry criteria to append.
     *
     * @return A new {@code RetryPolicy} instance with the passed criteria appended and all existing properties of
     * this policy.
     */
    public RetryPolicy appendCriteria(Func1<Throwable, Boolean> retryCriteria) {
        return new RetryPolicy(maxRetries,
                               throwable -> this.retryCriteria.call(throwable) || retryCriteria.call(throwable),
                               resumeStreamFunc, Actions.empty());
    }

    /**
     * Bounds the maximum retries to the passed number.
     *
     * @param maxRetries Maximum number of retries.
     *
     * @return A new {@code RetryPolicy} instance with the passed number of max retries and all existing properties of
     * this policy.
     */
    public RetryPolicy maxRetries(int maxRetries) {
        return new RetryPolicy(maxRetries, retryCriteria, resumeStreamFunc, Actions.empty());
    }

    /**
     * Same as calling {@link #backoffRetries(int, Scheduler)} with {@code Schedulers.computation()}
     */
    public RetryPolicy backoffRetries(int startDelayMillis) {
        return backoffRetries(startDelayMillis, Schedulers.computation());
    }

    /**
     * Exponential backoff retries based on the algorithm as used in
     * <a href="https://github.com/google/google-http-java-client/blob/dev/google-http-client/src/main/java/com/google/api/client/util/ExponentialBackOff.java">google HTTP client</a>
     * with a randomization factor of 0.5 and a multiplier of 1.5 and a max delay of 1 minute. <p>
     *
     * Since, the max retries are always bounded, the maximum delay of 1 minute would reach for really high values
     * of {@code startDelayMillis} or max retries. If the maximum delay is reached, the retries will still continue
     * but with max delay always.
     *
     * @param startDelayMillis Starting delay in milliseconds. This should usually be a very low value and can be 0.
     * @param timerScheduler Scheduler used for the delay.
     *
     * @return A new instance of {@code RetryPolicy} with the exponential backoff applied on top of the existing
     * configurations.
     */
    public RetryPolicy backoffRetries(int startDelayMillis, Scheduler timerScheduler) {
        if (startDelayMillis < 0) {
            throw new IllegalArgumentException("Start delay can not be negative.");
        }

        /*The below function, using this array will never get called concurrently and will always be called by the
        same thread.*/
        final double[] currentIntervalMillis = new double[] {startDelayMillis};

        return new RetryPolicy(maxRetries, retryCriteria, throwable -> {
            final double currentInterval = currentIntervalMillis[0];
            double delta = 0.5 * currentInterval;
            double minInterval = currentInterval - delta;
            double maxInterval = currentInterval + delta;
            // Get a random value from the range [minInterval, maxInterval].

            // The formula used below has a +1 because if the minInterval is 1 and the maxInterval is 3 then
            // we want a 33% chance for selecting either 1, 2 or 3.
            int randomValue = (int) (minInterval + (Math.random() * (maxInterval - minInterval + 1)));
            currentIntervalMillis[0] = Math.min(currentInterval * 1.5, 60000); /*Max of 1 minute*/
            return Observable.timer(randomValue, TimeUnit.MILLISECONDS, timerScheduler);
        }, Actions.empty());
    }

    /**
     * Invokes the passed action, whenever this policy determines the request is to be retried.
     *
     * @param beforeRetry Action to invoke before every retry.
     *
     * @return A new {@code RetryPolicy} instance with the all existing properties of this policy and the passed retry
     * action to be performed before retry.
     */
    public RetryPolicy doBeforeRetry(Action0 beforeRetry) {
        return new RetryPolicy(maxRetries, retryCriteria, resumeStreamFunc, beforeRetry);
    }
}
