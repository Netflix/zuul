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
package com.netflix.zuul.lifecycle;

import java.util.ArrayList;
import java.util.List;

import com.netflix.zuul.filter.ErrorFilter;
import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.filter.PostFilter;
import com.netflix.zuul.filter.PreFilter;
import com.netflix.zuul.filter.RouteFilter;

/**
 * Simple data holder that defines all filters for a given route and enforces
 * sortedness by {@link com.netflix.zuul.filter.Filter#getOrder()}.
 */
public class FiltersForRoute<Request, Response> {
    private final List<PreFilter<Request>> preFilters;
    private final RouteFilter<Request> routeFilter;
    private final List<PostFilter<Response>> postFilters;
    private final ErrorFilter<Response> errorFilter;

    public FiltersForRoute(List<PreFilter<Request>> preFilters, RouteFilter<Request> routeFilter, List<PostFilter<Response>> postFilters, ErrorFilter<Response> errorFilter) {
        this.preFilters = preFilters;
        this.preFilters.sort((f1, f2) -> f1.getOrder() - f2.getOrder());
        this.routeFilter = routeFilter;
        this.postFilters = postFilters;
        this.postFilters.sort((f1, f2) -> f1.getOrder() - f2.getOrder());
        this.errorFilter = errorFilter;
    }

    public List<PreFilter<Request>> getPreFilters() {
        return preFilters;
    }

    public RouteFilter<Request> getRouteFilter() {
        return routeFilter;
    }

    public List<PostFilter<Response>> getPostFilters() {
        return postFilters;
    }

    public ErrorFilter<Response> getErrorFilter() {
        return errorFilter;
    }

    public List<Filter> all() {
        List<Filter> all = new ArrayList<>();
        all.addAll(preFilters);
        all.add(routeFilter);
        all.addAll(postFilters);
        all.add(errorFilter);
        return all;
    }
}
