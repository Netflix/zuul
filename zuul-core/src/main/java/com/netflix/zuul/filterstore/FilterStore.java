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

import com.netflix.zuul.filter.Filter;
import com.netflix.zuul.lifecycle.FiltersForRoute;
import com.netflix.zuul.lifecycle.IngressRequest;
import com.netflix.zuul.metrics.ZuulFilterMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetricsPublisherFactory;

import java.io.IOException;

public abstract class FilterStore<Request, Response> {
    protected abstract FiltersForRoute<Request, Response> fetchFilters(IngressRequest ingressReq) throws IOException;

    public FiltersForRoute<Request, Response> getFilters(IngressRequest ingressReq) throws IOException {
        FiltersForRoute<Request, Response> filters = fetchFilters(ingressReq);
        for (Filter f: filters.all()) {
            ZuulFilterMetricsPublisher filterMetricsPublisher = ZuulMetricsPublisherFactory.createOrRetrieveFilterPublisher(f.getClass());
        }
        return filters;
    }
}
