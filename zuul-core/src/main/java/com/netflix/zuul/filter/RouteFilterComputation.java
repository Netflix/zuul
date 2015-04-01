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

import com.netflix.zuul.metrics.ZuulMetrics;
import rx.Observable;

/**
 * TODO - is this class basically a replacement for StaticResponse filters?
 *
 * @param <T>
 */
public abstract class RouteFilterComputation<T> implements RouteFilter<T> {

    public abstract T provideResponse(T context);

    @Override
    public Observable<T> execute(T context) {
        final long startTime = System.currentTimeMillis();
        try {
            Observable<T> resp = Observable.just(provideResponse(context));
            ZuulMetrics.markFilterSuccess(getClass(), System.currentTimeMillis() - startTime);
            return resp;
        } catch (Throwable ex) {
            ZuulMetrics.markFilterFailure(getClass(), System.currentTimeMillis() - startTime);
            throw ex;
        }
    }
}
