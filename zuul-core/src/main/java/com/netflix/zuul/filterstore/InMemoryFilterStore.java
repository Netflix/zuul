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
package com.netflix.zuul.filterstore;

import com.netflix.zuul.filter.*;
import com.netflix.zuul.lifecycle.FiltersForRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryFilterStore<State> extends FilterStore<State> {
    private final static Logger logger = LoggerFactory.getLogger(InMemoryFilterStore.class);

    private final CopyOnWriteArrayList<PreFilter<State>> preFilters = new CopyOnWriteArrayList<>();
    private final AtomicReference<RouteFilter<State>> routeFilter = new AtomicReference<>();
    private final CopyOnWriteArrayList<PostFilter<State>> postFilters = new CopyOnWriteArrayList<>();
    private final AtomicReference<ErrorFilter<State>> errorFilter = new AtomicReference<>();

    @Override
    public void init()
    {
        // Noop.
    }

    @Override
    public FiltersForRoute<State> fetchFilters() {
        return new FiltersForRoute<>(preFilters, routeFilter.get(), postFilters, errorFilter.get());
    }

    @SuppressWarnings("unchecked")
    public void addFilter(Filter filter) {
        if (filter instanceof PreFilter) {
            preFilters.add((PreFilter<State>) filter);
        } else if (filter instanceof PostFilter) {
            postFilters.add((PostFilter<State>) filter);
        } else if (filter instanceof RouteFilter) {
            routeFilter.lazySet((RouteFilter<State>) filter);
        } else if (filter instanceof ErrorFilter) {
            errorFilter.lazySet((ErrorFilter<State>) filter);
        } else {
            logger.error("Unknown filter type : " + filter + " : " + filter.getClass().getCanonicalName());
        }
    }
}
