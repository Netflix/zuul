package com.netflix.zuul.rxnetty.origin;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import rx.Observable;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;
import rx.observers.TestSubscriber;
import rx.schedulers.TestScheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class RetryPolicyTest {

    @Rule
    public final RetrySource source = new RetrySource();

    @Test(timeout = 60000)
    public void testSingleRetry() throws Exception {
        RetryPolicy policy = RetryPolicy.createSingleRetry(throwable -> throwable instanceof NullPointerException);

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();

        source.source.retryWhen(policy).subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertError(NullPointerException.class);

        assertThat("Unexpected number of retries.", source.getRetryCount(), is(1));
    }

    @Test(timeout = 60000)
    public void testMaxRetries() throws Exception {
        RetryPolicy policy = RetryPolicy.createSingleRetry(throwable -> throwable instanceof NullPointerException)
                                        .maxRetries(2);

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();

        source.source.retryWhen(policy).subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertError(NullPointerException.class);

        assertThat("Unexpected number of retries.", source.getRetryCount(), is(2));
    }

    @Test(timeout = 60000)
    public void testBackoff() throws Exception {

        UberTestScheduler testScheduler = new UberTestScheduler();

        RetryPolicy policy = RetryPolicy.createSingleRetry(throwable -> throwable instanceof NullPointerException)
                                        .maxRetries(2)
                                        .backoffRetries(100, testScheduler);

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();

        source.source.retryWhen(policy).subscribe(testSubscriber);

        executeNextRetry("First ", testScheduler, testSubscriber);

        testSubscriber.assertNoTerminalEvent();

        executeNextRetry("Second ", testScheduler, testSubscriber);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertError(NullPointerException.class);

        assertThat("Unexpected number of retries.", source.getRetryCount(), is(2));
    }

    @Test(timeout = 60000)
    public void testAppend() throws Exception {
        RetryPolicy policy = RetryPolicy.createSingleRetry(throwable -> throwable instanceof IllegalArgumentException)
                                        .appendCriteria(throwable -> throwable instanceof NullPointerException);

        TestSubscriber<String> testSubscriber = new TestSubscriber<>();

        source.source.retryWhen(policy).subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertError(NullPointerException.class);

        assertThat("Unexpected number of retries.", source.getRetryCount(), is(1));
    }

    private WorkItem executeNextRetry(String workerName, UberTestScheduler testScheduler,
                                      TestSubscriber<String> testSubscriber) {
        testSubscriber.assertNoTerminalEvent();

        assertThat(workerName + " retry worker not created.", testScheduler.getWorkers(), hasSize(1));

        final LooseWorker worker = testScheduler.getWorkers().peek();

        assertThat(workerName + " retry work not scheduled.", worker.delayedWorkItems, hasSize(1));
        final WorkItem work = worker.delayedWorkItems.peek();

        assertThat("Unexpected retry time", work.delayTime, greaterThan(0L));
        assertThat("Unexpected retry time", work.delayTime, lessThanOrEqualTo(60000L));
        assertThat("Unexpected retry time", work.timeUnit, equalTo(TimeUnit.MILLISECONDS));

        testScheduler.advanceTimeBy(work.delayTime, work.timeUnit);

        return work;
    }

    public static class RetrySource extends ExternalResource {

        private Observable<String> source;
        private int subscriptionCount;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    subscriptionCount = 0;
                    source = Observable.<String>create(subscriber -> {
                        subscriber.onError(new NullPointerException());
                        ++subscriptionCount;
                    });
                    base.evaluate();
                }
            };
        }

        public int getRetryCount() {
            return Math.max(0, subscriptionCount - 1);
        }
    }

    private static class UberTestScheduler extends TestScheduler {

        private ConcurrentLinkedQueue<LooseWorker> workers = new ConcurrentLinkedQueue<>();

        @Override
        public Worker createWorker() {
            LooseWorker worker = new LooseWorker(super.createWorker(), workers);
            workers.add(worker);
            return worker;
        }

        public Queue<LooseWorker> getWorkers() {
            return workers;
        }
    }

    private static class LooseWorker extends Worker {

        private final Worker delegate;
        private final ConcurrentLinkedQueue<LooseWorker> workers;
        private final ConcurrentLinkedQueue<WorkItem> delayedWorkItems = new ConcurrentLinkedQueue<>();

        public LooseWorker(Worker delegate, ConcurrentLinkedQueue<LooseWorker> workers) {
            this.delegate = delegate;
            this.workers = workers;
        }

        @Override
        public Subscription schedule(Action0 action) {
            return delegate.schedule(action);
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            delayedWorkItems.add(new WorkItem(delayTime, unit));
            return delegate.schedule(action, delayTime, unit);
        }

        @Override
        public void unsubscribe() {
            workers.remove(this);
            delayedWorkItems.clear();
            delegate.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return delegate.isUnsubscribed();
        }

    }

    private static class WorkItem {

        private final long delayTime;
        private final TimeUnit timeUnit;

        private WorkItem(long delayTime, TimeUnit timeUnit) {
            this.delayTime = delayTime;
            this.timeUnit = timeUnit;
        }
    }
}