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
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.exception.ErrorType;
import com.netflix.zuul.exception.OutboundErrorType;

import java.util.concurrent.TimeUnit;


/**
 * @author michaels
 */
public class BasicRequestStat implements RequestStat {

    private volatile boolean isFinished;
    private volatile Stopwatch stopwatch;

    public BasicRequestStat() {
        this.isFinished = false;
        this.stopwatch = Stopwatch.createStarted();
    }

    @Override
    public RequestStat server(DiscoveryResult server) {
        return this;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public long duration() {
        long ms = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        return ms > 0 ? ms : 0;
    }

    @Override
    public void serviceUnavailable() {
        failAndSetErrorCode(OutboundErrorType.SERVICE_UNAVAILABLE);
    }

    @Override
    public void generalError() {
        failAndSetErrorCode(OutboundErrorType.OTHER);
    }

    @Override
    public void failAndSetErrorCode(ErrorType error) {
        // override to implement metric tracking
    }

    @Override
    public void updateWithHttpStatusCode(int httpStatusCode) {
        // override to implement metric tracking
    }

    @Override
    public void finalAttempt(boolean finalAttempt) {}

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
