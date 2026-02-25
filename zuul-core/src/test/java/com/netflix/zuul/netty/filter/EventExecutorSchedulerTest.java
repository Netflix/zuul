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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;

/**
 * @author Justin Guerra
 * @since 5/20/25
 */
class EventExecutorSchedulerTest {

    private MultithreadEventLoopGroup group;
    private EventLoop eventLoop;
    private EventExecutorScheduler scheduler;

    @BeforeEach
    void setUp() {
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        eventLoop = group.next();
        scheduler = new EventExecutorScheduler(eventLoop);
    }

    @AfterEach
    void tearDown() {
        group.shutdownGracefully();
    }

    @Test
    void nullExecutor() {
        assertThatThrownBy(() -> new EventExecutorScheduler(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void alreadyOnEventLoopImmediatelyRuns() {
        Worker worker = scheduler.createWorker();

        CountDownLatch latch = new CountDownLatch(1);
        eventLoop.execute(() -> {
            AtomicBoolean executed = new AtomicBoolean(false);
            Action0 action = () -> {
                executed.set(true);
            };
            Subscription schedule = worker.schedule(action);
            assertThat(executed.get()).isTrue();
            assertThat(schedule.isUnsubscribed()).isTrue();
            latch.countDown();
        });

        if (!Uninterruptibles.awaitUninterruptibly(latch, 5, TimeUnit.SECONDS)) {
            fail("schedule did not complete in a reasonable amount of time");
        }
    }

    @Test
    void nonEventLoopThreadExecutedOnEventLoop() throws Exception {
        AtomicBoolean inEventLoop = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        Action0 action = () -> {
            inEventLoop.set(eventLoop.inEventLoop());
            if (!Uninterruptibles.awaitUninterruptibly(latch, 5, TimeUnit.SECONDS)) {
                fail("action not completed in a reasonable amount of time");
            }
        };

        Worker worker = scheduler.createWorker();
        Subscription schedule = worker.schedule(action);
        assertThat(schedule.isUnsubscribed()).isFalse();

        latch.countDown();
        // ensure the original action finished
        eventLoop.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertThat(inEventLoop.get()).isTrue();
        assertThat(schedule.isUnsubscribed()).isTrue();
    }

    @Test
    void workerUnsubscribe() {
        Worker worker = scheduler.createWorker();
        assertThat(worker.isUnsubscribed()).isFalse();
        worker.unsubscribe();
        assertThat(worker.isUnsubscribed()).isTrue();
    }
}
