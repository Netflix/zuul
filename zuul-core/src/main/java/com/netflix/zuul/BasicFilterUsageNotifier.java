/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul;

import com.netflix.spectator.api.Registry;
import com.netflix.zuul.filters.ZuulFilter;
import javax.inject.Inject;

/**
 * Publishes a counter metric for each filter on each use.
 */
public class BasicFilterUsageNotifier implements FilterUsageNotifier {
    private static final String METRIC_PREFIX = "zuul.filter-";
    private final Registry registry;

    @Inject
    BasicFilterUsageNotifier(Registry registry) {
        this.registry = registry;
    }

    @Override
    public void notify(ZuulFilter<?, ?> filter, ExecutionStatus status) {
        registry.counter(
                "zuul.filter-" + filter.getClass().getSimpleName(),
                "status", status.name(),
                "filtertype", filter.filterType().toString()).increment();
    }
}

