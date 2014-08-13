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

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryFilterStore implements FilterStore {
    private final CopyOnWriteArrayList<PreFilter> preFilters = new CopyOnWriteArrayList<>();
    private final AtomicReference<RouteFilter> routeFilter = new AtomicReference<>();
    private final CopyOnWriteArrayList<PostFilter> postFilters = new CopyOnWriteArrayList<>();

    @Override
    public FiltersForRoute getFilters(IngressRequest ingressReq) {
        return new FiltersForRoute(preFilters, routeFilter.get(), postFilters);
    }

    @Override
    public void addFilter(Filter filter) {
        if (filter instanceof PreFilter) {
            preFilters.add((PreFilter) filter);
        } else if (filter instanceof PostFilter) {
            postFilters.add((PostFilter) filter);
        } else if (filter instanceof RouteFilter) {
            routeFilter.lazySet((RouteFilter) filter);
        } else {
            System.err.println("Unknown filter type : " + filter + " : " + filter.getClass().getCanonicalName());
        }
    }
}
