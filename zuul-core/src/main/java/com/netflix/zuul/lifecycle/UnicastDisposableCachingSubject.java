package com.netflix.zuul.lifecycle;

import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action1;
import rx.internal.operators.NotificationLite;
import rx.observers.Subscribers;
import rx.subjects.Subject;
import rx.subscriptions.Subscriptions;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link Subject} implementation for caching {@link ReferenceCounted} objects which can be disposed if not sent to
 * the sole subscriber of this subject.
 *
 * @author Nitesh Kant
 */
final class UnicastDisposableCachingSubject<T extends ReferenceCounted> extends Subject<T, T> {

    private final State<T> state;

    private UnicastDisposableCachingSubject(State<T> state) {
        super(new OnSubscribeAction<>(state));
        this.state = state;
    }

    public static <T extends ReferenceCounted> UnicastDisposableCachingSubject<T> create() {
        State<T> state = new State<>();
        return new UnicastDisposableCachingSubject<>(state);
    }

    /**
     * Disposes the items held by this subject which were not given out to the lone subscriber.
     *
     * @param disposedElementsProcessor All elements that were disposed are passed to this processor.
     */
    public void dispose(Action1<T> disposedElementsProcessor) {
        if (state.casState(State.STATES.UNSUBSCRIBED, State.STATES.DISPOSED)) {
            _dispose(disposedElementsProcessor);
        } else if (state.casState(State.STATES.SUBSCRIBED, State.STATES.DISPOSED)) {
            state.observerRef.onCompleted(); // Complete the existing subscription in case it is still not unsubscribed.
            _dispose(disposedElementsProcessor);
        }
    }

    private void _dispose(Action1<T> disposedElementsProcessor) {
        Subscriber<T> noOpSub = new PassThruObserver<>(Subscribers.create(disposedElementsProcessor,
                                                                          throwable -> { }),
                                                       state); // Any buffering post buffer draining must not be lying in the buffer
        state.buffer.sendAllNotifications(noOpSub); // It is important to empty the buffer before setting the observer.
                                                    // If not done, there can be two threads draining the buffer
                                                    // (PassThroughObserver on any notification) and this thread.
        state.setObserverRef(noOpSub); // All future notifications are not sent anywhere.
    }

    /** The common state. */
    private static final class State<T> {

        /**
         * Following are the only possible state transitions:
         * UNSUBSCRIBED -> SUBSCRIBED
         * UNSUBSCRIBED -> DISPOSED
         */
        private enum STATES {
            UNSUBSCRIBED /*Initial*/, SUBSCRIBED /*Terminal state*/, DISPOSED/*Terminal state*/
        }

        private volatile int state = STATES.UNSUBSCRIBED.ordinal(); /*Values are the ordinals of STATES enum*/

        /** Following Observers are associated with the states:
         * UNSUBSCRIBED => {@link BufferedObserver}
         * SUBSCRIBED => {@link PassThruObserver}
         * DISPOSED => {@link Subscribers#empty()}
         */
        private volatile Observer<? super T> observerRef = new BufferedObserver();

        /**
         * The only buffer associated with this state. All notifications go to this buffer if no one has subscribed and
         * the {@link UnicastDisposableCachingSubject} instance is not disposed.
         */
        private final ByteBufAwareBuffer<T> buffer = new ByteBufAwareBuffer<>();

        /** Field updater for observerRef. */
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<State, Observer> OBSERVER_UPDATER
                = AtomicReferenceFieldUpdater.newUpdater(State.class, Observer.class, "observerRef");

        /** Field updater for state. */
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<State> STATE_UPDATER
                = AtomicIntegerFieldUpdater.newUpdater(State.class, "state");

        public boolean casState(STATES expected, STATES next) {
            return STATE_UPDATER.compareAndSet(this, expected.ordinal(), next.ordinal());
        }

        public void setObserverRef(Observer<? super T> o) { // Guarded by casState()
            observerRef = o;
        }

        public boolean casObserverRef(Observer<? super T> expected, Observer<? super T> next) {
            return OBSERVER_UPDATER.compareAndSet(this, expected, next);
        }

        /**
         * The default subscriber when the enclosing state is created.
         */
        private final class BufferedObserver extends Subscriber<T> {

            private final NotificationLite<Object> nl = NotificationLite.instance();

            @Override
            public void onCompleted() {
                buffer.add(nl.completed());
            }

            @Override
            public void onError(Throwable e) {
                buffer.add(nl.error(e));
            }

            @Override
            public void onNext(T t) {
                buffer.add(nl.next(t));
            }
        }
    }

    private static final class OnSubscribeAction<T> implements OnSubscribe<T> {

        private final State<T> state;

        public OnSubscribeAction(State<T> state) {
            this.state = state;
        }

        @Override
        public void call(final Subscriber<? super T> subscriber) {
            if (state.casState(State.STATES.UNSUBSCRIBED, State.STATES.SUBSCRIBED)) {

                // drain queued notifications before subscription
                // we do this here before PassThruObserver so the consuming thread can do this before putting itself in
                // the line of the producer
                state.buffer.sendAllNotifications(subscriber);

                // register real observer for pass-thru ... and drain any further events received on first notification
                state.setObserverRef(new PassThruObserver<>(subscriber, state));
                subscriber.add(Subscriptions.create(() -> state.setObserverRef(Subscribers.empty())));
            } else if(State.STATES.SUBSCRIBED.ordinal() == state.state) {
                subscriber.onError(new IllegalStateException("Content can only have one subscription. Use Observable.publish() if you want to multicast."));
            } else if(State.STATES.DISPOSED.ordinal() == state.state) {
                subscriber.onError(new IllegalStateException("Content stream is already disposed."));
            }
        }

    }

    @Override
    public void onCompleted() {
        state.observerRef.onCompleted();
    }

    @Override
    public void onError(Throwable e) {
        state.observerRef.onError(e);
    }

    @Override
    public void onNext(T t) {
        state.observerRef.onNext(t);
    }

    /**
     * This is a temporary observer between buffering and the actual that gets into the line of notifications
     * from the producer and will drain the queue of any items received during the race of the initial drain and
     * switching this.
     *
     * It will then immediately swap itself out for the actual (after a single notification), but since this is
     * now being done on the same producer thread no further buffering will occur.
     */
    private static final class PassThruObserver<T> extends Subscriber<T> {

        private final Observer<? super T> actual;
        // this assumes single threaded synchronous notifications (the Rx contract for a single Observer)
        private final ByteBufAwareBuffer<T> buffer; // Same buffer instance from the original BufferedObserver.
        private final State<T> state;

        PassThruObserver(Observer<? super T> actual, State<T> state) {
            this.actual = actual;
            buffer = state.buffer;
            this.state = state;
        }

        @Override
        public void onCompleted() {
            drainIfNeededAndSwitchToActual();
            actual.onCompleted();
        }

        @Override
        public void onError(Throwable e) {
            drainIfNeededAndSwitchToActual();
            actual.onError(e);
        }

        @Override
        public void onNext(T t) {
            drainIfNeededAndSwitchToActual();
            actual.onNext(t);
        }

        private void drainIfNeededAndSwitchToActual() {
            buffer.sendAllNotifications(this);
            // now we can safely change over to the actual and get rid of the pass-thru
            // but only if not unsubscribed
            state.casObserverRef(this, actual);
        }
    }

    private static final class ByteBufAwareBuffer<T> {

        private final ConcurrentLinkedQueue<Object> actual = new ConcurrentLinkedQueue<>();
        private final NotificationLite<T> nl = NotificationLite.instance();

        private void add(Object toAdd) {
            ReferenceCountUtil.retain(toAdd); // Released when the notification is sent.
            actual.add(toAdd);
        }

        public void sendAllNotifications(Subscriber<? super T> subscriber) {
            Object notification; // Can be onComplete notification, onError notification or just the actual "T".
            while ((notification = actual.poll()) != null) {
                try {
                    nl.accept(subscriber, notification);
                } finally {
                    ReferenceCountUtil.release(notification); // If it is the actual T for onNext and is a ByteBuf, it will be released.
                }
            }
        }
    }
}
