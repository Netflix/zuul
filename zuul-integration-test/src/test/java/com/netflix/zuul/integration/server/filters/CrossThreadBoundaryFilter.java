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

package com.netflix.zuul.integration.server.filters;

import com.netflix.zuul.Filter;
import com.netflix.zuul.filters.FilterSyncType;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import rx.Observable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Justin Guerra
 * @since 5/20/25
 */
@Filter(order = 30, type = FilterType.INBOUND, sync = FilterSyncType.ASYNC)
public class CrossThreadBoundaryFilter extends HttpInboundFilter {

    private final ExecutorService executor;

    public CrossThreadBoundaryFilter() {
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public Observable<HttpRequestMessage> applyAsync(HttpRequestMessage input) {
        // force a thread boundary change
        Future<HttpRequestMessage> future = executor.submit(() -> input);
        return Observable.from(future);
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage msg) {
        return true;
    }
}
