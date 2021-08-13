/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul.filters;

import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observer;

final class FilterHelper {

    private static final Logger logger = LoggerFactory.getLogger(FilterHelper.class);

    static <O> void observerToPromise(Promise<? super O> promise, Observable<? extends O> observable) {
        // TODO(carl-mastrangelo): add tracing links for this adapter.
        observable.subscribe(new Observer<O>() {
            @Override
            public void onCompleted() {}

            @Override
            public void onError(Throwable t) {
                if (!promise.tryFailure(t)) {
                    if (!promise.isSuccess()) {
                        logger.debug("promise already failed", t);
                    } else {
                        // TODO(carl-mastrangelo): increment a counter for this case.
                        logger.warn("promise already succeed {}", promise.getNow(), t);
                    }
                }
            }

            @Override
            public void onNext(O o) {
                if (!promise.trySuccess(o)) {
                    if (!promise.isSuccess()) {
                        logger.debug("promise already failed", promise.cause());
                    } else {
                        // TODO(carl-mastrangelo): increment a counter for this case.
                        logger.warn("promise already succeed {} << {}", promise.getNow(), o);
                    }
                }
            }
        });
    }

    private FilterHelper() {}
}
