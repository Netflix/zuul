/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.filter;

import rx.Observable;

import com.netflix.zuul.lifecycle.EgressResponse;
import com.netflix.zuul.metrics.ZuulMetrics;

public abstract class ErrorFilterComputation<T> implements ErrorFilter<T> {
    public abstract EgressResponse<T> provideResponse(Throwable ex);

    @Override
    public Observable<EgressResponse<T>> execute(Throwable ex) {
        final long startTime = System.currentTimeMillis();
        try {
            Observable<EgressResponse<T>> resp = Observable.just(provideResponse(ex));
            ZuulMetrics.markFilterSuccess(getClass(), System.currentTimeMillis() - startTime);
            return resp;
        } catch (Throwable filterEx) {
            ZuulMetrics.markFilterFailure(getClass(), System.currentTimeMillis() - startTime);
            throw filterEx;
        }
    }
}
