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

package com.netflix.zuul.sample;

import rx.Observable;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample Service for demonstration in SampleServiceFilter.
 *
 * Author: Arthur Gonigberg
 * Date: January 04, 2018
 */
@Singleton
public class SampleService {

    private final AtomicBoolean status;

    public SampleService() {
        // change to true for demo
        this.status = new AtomicBoolean(false);
    }

    public boolean isHealthy() {
        return status.get();
    }

    public Observable<String> makeSlowRequest() {
        return Observable.just("test").delay(500, TimeUnit.MILLISECONDS);
    }
}
