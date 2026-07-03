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
import java.util.ArrayList;
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

    private static final List<FilterConstraint> NONE = List.of();

    private final Map<Class<? extends FilterConstraint>, FilterConstraint> lookup;
    private final Map<Class<?>, List<FilterConstraint>> filterConstraints;

    public FilterConstraints(List<FilterConstraint> constraints) {
        this.lookup = constraints.stream().collect(Collectors.toUnmodifiableMap(FilterConstraint::getClass, c -> c));
        this.filterConstraints = new ConcurrentHashMap<>();
    }

    /**
     * Checks if any {@link FilterConstraint}'s are active for the given msg
     */
    public boolean isConstrained(ZuulMessage msg, ZuulFilter<?, ?> filter) {
        List<FilterConstraint> constraints = this.filterConstraints.get(filter.getClass());
        if (constraints == null) {
            // avoid computeIfAbsent directly so the lambda isn't allocated when constraints are already cached
            constraints = this.filterConstraints.computeIfAbsent(filter.getClass(), k -> this.resolve(filter));
        }

        if (constraints.isEmpty()) {
            return false;
        }

        // intentionally using an index loop rather than an enhanced-for
        // to avoid allocations on this hot path
        for (int i = 0; i < constraints.size(); i++) {
            if (constraints.get(i).isConstrained(msg)) {
                return true;
            }
        }

        return false;
    }

    private List<FilterConstraint> resolve(ZuulFilter<?, ?> filter) {
        Class<? extends FilterConstraint>[] declared = filter.constraints();
        if (declared == null || declared.length == 0) {
            return NONE;
        }

        List<FilterConstraint> resolvedConstraints = new ArrayList<>(declared.length);
        for (Class<? extends FilterConstraint> c : declared) {
            FilterConstraint fc = this.lookup.get(c);
            if (fc != null) {
                resolvedConstraints.add(fc);
            }
        }

        return List.copyOf(resolvedConstraints);
    }
}
