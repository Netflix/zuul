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

/**
 * Base interface which contains logic for processing a request/response
 * @param <I> input type
 */
public interface ProcessingFilter<I> extends Filter {
    /**
     * Given an input, execute the filter and return the output
     * @param input input to filter
     * @return filter output
     */
    public Observable<I> execute(Observable<I> input);
}