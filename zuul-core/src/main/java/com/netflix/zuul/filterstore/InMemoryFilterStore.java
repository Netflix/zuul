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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.zuul.filter.ErrorFilter;
import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.filter.PostFilter;
import com.netflix.zuul.filter.PreFilter;
import com.netflix.zuul.filter.RouteFilter;
import com.netflix.zuul.lifecycle.FiltersForRoute;
import com.netflix.zuul.lifecycle.IngressRequest;

public class InMemoryFilterStore<Request, Response> extends FilterStore<Request, Response> {
    private final CopyOnWriteArrayList<PreFilter<Request>> preFilters = new CopyOnWriteArrayList<>();
    private final AtomicReference<RouteFilter<Request>> routeFilter = new AtomicReference<>();
    private final CopyOnWriteArrayList<PostFilter<Response>> postFilters = new CopyOnWriteArrayList<>();
    private final AtomicReference<ErrorFilter<Response>> errorFilter = new AtomicReference<>();

    @Override
    public FiltersForRoute<Request, Response> fetchFilters(IngressRequest ingressReq) {
        return new FiltersForRoute<Request, Response>(preFilters, routeFilter.get(), postFilters, errorFilter.get());
    }

    @SuppressWarnings("unchecked")
    public void addFilter(Filter filter) {
        if (filter instanceof PreFilter) {
            preFilters.add((PreFilter<Request>) filter);
        } else if (filter instanceof PostFilter) {
            postFilters.add((PostFilter<Response>) filter);
        } else if (filter instanceof RouteFilter) {
            routeFilter.lazySet((RouteFilter<Request>) filter);
        } else if (filter instanceof ErrorFilter) {
            errorFilter.lazySet((ErrorFilter<Response>) filter);
        } else {
            System.err.println("Unknown filter type : " + filter + " : " + filter.getClass().getCanonicalName());
        }
    }
}
