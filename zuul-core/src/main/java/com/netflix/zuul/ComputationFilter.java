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
package com.netflix.zuul;

import rx.Observable;

public abstract class ComputationFilter<I> implements ProcessingFilter<I> {
    public abstract Boolean shouldFilter(I input);
    public abstract I apply(I input);

    @Override
    public Observable<I> execute(Observable<I> input) {
        return input.map(result -> {
            if (shouldFilter(result)) {
                return apply(result);
            } else {
                return result;
            }
        });
    }
}