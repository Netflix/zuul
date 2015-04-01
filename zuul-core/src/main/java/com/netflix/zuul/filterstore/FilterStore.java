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

import com.netflix.zuul.lifecycle.FiltersForRoute;

import java.io.IOException;

public abstract class FilterStore<State>
{
    public abstract void init();

    protected abstract FiltersForRoute<State> fetchFilters() throws IOException;

    public FiltersForRoute<State> getFilters() throws IOException
    {
        FiltersForRoute<State> filters = fetchFilters();
//        for (Filter f: filters.all()) {
//            ZuulFilterMetricsPublisher filterMetricsPublisher = ZuulMetricsPublisherFactory.createOrRetrieveFilterPublisher(f.getClass());
//        }
        return filters;
    }
}
