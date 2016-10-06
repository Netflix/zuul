/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.rx;

import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.internal.operators.NotificationLite;
import rx.observers.SerializedObserver;
import rx.observers.Subscribers;
import rx.subjects.Subject;
import rx.subscriptions.Subscriptions;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link rx.subjects.Subject} implementation for caching {@link io.netty.util.ReferenceCounted} objects which can be disposed if not sent to
 * the sole subscriber of this subject.
 *
 * @author Nitesh Kant
 */
public final class UnicastDisposableCachingSubject<T extends ReferenceCounted> extends Subject<T, T> {

    private final State<T> state;

    private UnicastDisposableCachingSubject(State<T> state) {
        super(new OnSubscribeAction<>(state));
        this.state = state;
    }

    public static <T extends ReferenceCounted> UnicastDisposableCachingSubject<T> create() {
        State<T> state = new State<>();
        return new UnicastDisposableCachingSubject<>(state);
    }

    @Override
    public boolean hasObservers() {
        return state.isState(State.STATES.SUBSCRIBED);
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
        Subscriber<T> actualSub = Subscribers.create(disposedElementsProcessor);
        state.buffer.sendAllNotifications(actualSub);
        state.setObserverRef(actualSub);
        // Any notifications that went into the buffer between the prior sendAllNotifications and swapping
        // the observer, will otherwise be lost.
        state.buffer.sendAllNotifications(state.observerRef); // Subscriber is always serialized (via setObserverRef) so this will not produce concurrent notifications.
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
         * SUBSCRIBED => actual subscriber
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
            observerRef = new SerializedObserver<>(o);
        }

        public boolean isState(STATES other) {
            return state == other.ordinal();
        }

        /**
         * The default subscriber when the enclosing state is created.
         */
        private final class BufferedObserver extends Subscriber<T> {

            @Override
            public void onCompleted() {
                buffer.add(NotificationLite.completed());
            }

            @Override
            public void onError(Throwable e) {
                buffer.add(NotificationLite.error(e));
            }

            @Override
            public void onNext(T t) {
                buffer.add(NotificationLite.next(t));
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
                // we do this here before subscriber so the consuming thread can do this before putting itself in
                // the line of the producer
                state.buffer.sendAllNotifications(subscriber);
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        state.setObserverRef(Subscribers.empty());
                    }
                }));
                state.setObserverRef(subscriber);
                // Any notifications that went into the buffer between the prior sendAllNotifications and swapping
                // the observer, will otherwise be lost.
                state.buffer.sendAllNotifications(state.observerRef); // Subscriber is always serialized (via setObserverRef) so this will not produce concurrent notifications.
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

    private static final class ByteBufAwareBuffer<T> {

        private final ConcurrentLinkedQueue<Object> actual = new ConcurrentLinkedQueue<>();

        private void add(Object toAdd) {
            ReferenceCountUtil.retain(toAdd); // Released when the notification is sent.
            actual.add(toAdd);
        }

        public void sendAllNotifications(Observer<? super T> observer) {
            Object notification; // Can be onComplete notification, onError notification or just the actual "T".
            while ((notification = actual.poll()) != null) {
                try {
                    NotificationLite.accept(observer, notification);
                } finally {
                    ReferenceCountUtil.release(notification); // If it is the actual T for onNext and is a ByteBuf, it will be released.
                }
            }
        }
    }
}