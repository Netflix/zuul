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

import java.util.List;

/**
 * Simple data holder that defines all filters for a given route and enforces
 * sortedness by {@link com.netflix.zuul.Filter#getOrder()}.
 */
public class FiltersForRoute {
    private final List<PreFilter> preFilters;
    private final RouteFilter routeFilter;
    private final List<PostFilter> postFilters;

    public FiltersForRoute(List<PreFilter> preFilters, RouteFilter routeFilter, List<PostFilter> postFilters) {
        this.preFilters = preFilters;
        this.preFilters.sort((f1, f2) -> f1.getOrder() - f2.getOrder());
        this.routeFilter = routeFilter;
        this.postFilters = postFilters;
        this.postFilters.sort((f1, f2) -> f1.getOrder() - f2.getOrder());
    }

    public List<PreFilter> getPreFilters() {
        return preFilters;
    }

    public RouteFilter getRouteFilter() {
        return routeFilter;
    }

    public List<PostFilter> getPostFilters() {
        return postFilters;
    }
}
