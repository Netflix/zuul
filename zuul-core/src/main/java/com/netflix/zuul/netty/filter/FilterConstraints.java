/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.zuul.netty.filter;

import com.netflix.zuul.FilterConstraint;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Class responsible for checking {@link FilterConstraint}.
 * Register this class with custom constraints by using {@link com.netflix.zuul.netty.server.ZuulDependencyKeys#filterConstraints}
 * in {@link com.netflix.zuul.netty.server.BaseZuulChannelInitializer}
 *
 * @author Justin Guerra
 * @since 1/9/26
 */
@NullMarked
public class FilterConstraints {

    @SuppressWarnings("unchecked")
    private static final Class<? extends FilterConstraint>[] NO_CONSTRAINTS = new Class[0];

    private final Map<Class<? extends FilterConstraint>, FilterConstraint> lookup;
    private final Map<String, Class<? extends FilterConstraint>[]> filterConstraints;

    public FilterConstraints(List<FilterConstraint> constraints) {
        this.lookup = constraints.stream().collect(Collectors.toUnmodifiableMap(FilterConstraint::getClass, c -> c));
        this.filterConstraints = new ConcurrentHashMap<>();
    }

    /**
     * Checks if any {@link FilterConstraint}'s are active for the given msg
     */
    public boolean isConstrained(ZuulMessage msg, ZuulFilter<?, ?> filter) {
        Class<? extends FilterConstraint>[] constraints =
                filterConstraints.computeIfAbsent(filter.getClass().getName(), f -> {
                    Class<? extends FilterConstraint>[] filterConstraints = filter.constraints();
                    return filterConstraints != null ? filterConstraints : NO_CONSTRAINTS;
                });

        for (Class<? extends FilterConstraint> constraint : constraints) {
            FilterConstraint filterConstraint = lookup.get(constraint);
            if (filterConstraint != null && filterConstraint.isConstrained(msg)) {
                return true;
            }
        }

        return false;
    }
}
