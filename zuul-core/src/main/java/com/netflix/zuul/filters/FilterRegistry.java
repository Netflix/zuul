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

import java.util.Collection;
import javax.annotation.Nullable;

public interface FilterRegistry {
    @Nullable
    ZuulFilter<?, ?> get(String key);

    int size();

    Collection<ZuulFilter<?, ?>> getAllFilters();

    /**
     * Indicates if this registry can be modified.  Implementations should not change the return;
     * they return the same value each time.
     */
    boolean isMutable();

    /**
     * Removes the filter from the registry, and returns it.   Returns {@code null} no such filter
     * was found.  Callers should check {@link #isMutable()} before calling this method.
     *
     * @throws IllegalStateException if this registry is not mutable.
     */
    @Nullable
    ZuulFilter<?, ?> remove(String key);

    /**
     * Stores the filter into the registry.  If an existing filter was present with the same key,
     * it is removed.  Callers should check {@link #isMutable()} before calling this method.
     *
     * @throws IllegalStateException if this registry is not mutable.
     */
    void put(String key, ZuulFilter<?, ?> filter);
}
