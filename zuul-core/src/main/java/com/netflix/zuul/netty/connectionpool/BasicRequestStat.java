/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.connectionpool;

import com.google.common.base.Stopwatch;
import com.netflix.loadbalancer.Server;

import java.util.concurrent.TimeUnit;

import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_ORIGIN;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_ORIGIN_THROTTLED;


/**
 * @author michaels
 */
public class BasicRequestStat implements RequestStat {

    private volatile boolean isFinished;
    private volatile Stopwatch stopwatch;

    public BasicRequestStat(String clientName) {
        this.isFinished = false;
        this.stopwatch = Stopwatch.createStarted();
    }

    @Override
    public RequestStat server(Server server) {
        return this;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public long duration() {
        if (!isFinished) {
            return -1;
        }
        long ns = stopwatch.elapsed(TimeUnit.NANOSECONDS);
        return ns > 0 ? ns / 1000000 : 0;
    }

    @Override
    public void serviceUnavailable() {
        failAndSetErrorCode(FAILURE_ORIGIN_THROTTLED.name());
    }

    @Override
    public void nextServerRetriesExceeded() {
        failAndSetErrorCode(FAILURE_ORIGIN_THROTTLED.name());
    }

    @Override
    public void generalError() {
        failAndSetErrorCode(FAILURE_ORIGIN.name());
    }

    @Override
    public void failAndSetErrorCode(String error) {
        // override to implement metric tracking
    }

    @Override
    public void updateWithHttpStatusCode(int httpStatusCode) {
        // override to implement metric tracking
    }

    @Override
    public boolean finishIfNotAlready() {
        if (isFinished) {
            return false;
        }
        stopwatch.stop();

        publishMetrics();

        isFinished = true;
        return true;
    }

    protected void publishMetrics() {
        // override to publish metrics here
    }
}
