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
package com.netflix.zuul.filters;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

@Singleton
public final class MutableFilterRegistry implements FilterRegistry {
    private final ConcurrentHashMap<String, ZuulFilter<?, ?>> filters = new ConcurrentHashMap<>();

    @Nullable @Override
    public ZuulFilter<?, ?> remove(String key) {
        return filters.remove(Objects.requireNonNull(key, "key"));
    }

    @Override
    @Nullable public ZuulFilter<?, ?> get(String key) {
        return filters.get(Objects.requireNonNull(key, "key"));
    }

    @Override
    public void put(String key, ZuulFilter<?, ?> filter) {
        filters.putIfAbsent(Objects.requireNonNull(key, "key"), Objects.requireNonNull(filter, "filter"));
    }

    @Override
    public int size() {
        return filters.size();
    }

    @Override
    public Collection<ZuulFilter<?, ?>> getAllFilters() {
        return Collections.unmodifiableList(new ArrayList<>(filters.values()));
    }

    @Override
    public boolean isMutable() {
        return true;
    }
}
