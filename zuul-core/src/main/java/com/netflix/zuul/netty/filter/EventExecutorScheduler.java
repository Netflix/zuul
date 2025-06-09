/*
 * Copyright 2025 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.netty.filter;

import io.netty.util.concurrent.EventExecutor;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.NotImplementedException;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.internal.schedulers.ScheduledAction;
import rx.subscriptions.Subscriptions;

/**
 * A custom {@link Scheduler} for use with a {@link EventExecutor} that
 * 1) Ensures that every action is run on the EventExecutor thread
 * 2) avoids the unnecessary executions that occur from using {@link rx.internal.schedulers.ExecutorScheduler} if already
 * executing on the correct thread
 *
 * Should only be used with {@link io.netty.channel.SingleThreadEventLoop}
 *
 * @author Justin Guerra
 * @since 5/19/25
 */
public class EventExecutorScheduler extends Scheduler {

    private final EventExecutor executor;

    public EventExecutorScheduler(EventExecutor executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    @Override
    public Worker createWorker() {
        return new Worker() {

            private volatile boolean unsubscribed;

            @Override
            public void unsubscribe() {
                unsubscribed = true;
            }

            @Override
            public boolean isUnsubscribed() {
                return unsubscribed;
            }

            @Override
            public Subscription schedule(Action0 action) {
                if (executor.inEventLoop()) {
                    action.call();
                    return Subscriptions.unsubscribed();
                } else {
                    ScheduledAction sa = new ScheduledAction(action);
                    executor.execute(sa);
                    return sa;
                }
            }

            @Override
            public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
                throw new NotImplementedException();
            }
        };
    }
}
