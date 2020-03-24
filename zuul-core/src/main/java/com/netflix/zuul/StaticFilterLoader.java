/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * An immutable static collection of filters.
 */
public final class StaticFilterLoader implements FilterLoader {

    private final Map<FilterType, ? extends List<ZuulFilter<?, ?>>> filtersByType;
    private final Map<FilterType, ? extends Map<String, ZuulFilter<?, ?>>> filtersByTypeAndName;

    @Inject
    public StaticFilterLoader(
            FilterFactory filterFactory, List<? extends Class<? extends ZuulFilter<?, ?>>> filterTypes) {
        Map<FilterType, ArrayList<ZuulFilter<?, ?>>> filtersByType = new EnumMap<>(FilterType.class);
        Map<FilterType, Map<String, ZuulFilter<?, ?>>> filtersByName = new EnumMap<>(FilterType.class);
        for (Class<? extends ZuulFilter<?, ?>> clz : filterTypes) {
            try {
                ZuulFilter<?, ?> f = filterFactory.newInstance(clz);
                filtersByType.computeIfAbsent(f.filterType(), k -> new ArrayList<>()).add(f);
                filtersByName.computeIfAbsent(f.filterType(), k -> new HashMap<>()).put(f.filterName(), f);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Map<FilterType, List<ZuulFilter<?, ?>>> sortedFilters = new EnumMap<>(FilterType.class);
        for (Entry<FilterType, ArrayList<ZuulFilter<?, ?>>> entry : filtersByType.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(ZuulFilter::filterOrder));
            entry.getValue().trimToSize();
            sortedFilters.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        Map<FilterType, Map<String, ZuulFilter<?, ?>>> immutableFiltersByName = new EnumMap<>(FilterType.class);
        for (Entry<FilterType, Map<String, ZuulFilter<?, ?>>> entry : filtersByName.entrySet()) {
            immutableFiltersByName.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        this.filtersByTypeAndName = Collections.unmodifiableMap(immutableFiltersByName);
        this.filtersByType = Collections.unmodifiableMap(sortedFilters);
    }

    @Override
    public boolean putFilter(File file) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ZuulFilter<?, ?>> putFiltersForClasses(String[] classNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ZuulFilter<?, ?> putFilterForClassName(String className) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ZuulFilter<?, ?>> getFiltersByType(FilterType filterType) {
        return filtersByType.get(filterType);
    }

    @Override
    @Nullable
    public ZuulFilter<?, ?> getFilterByNameAndType(String name, FilterType type) {
        Map<String, ZuulFilter<?, ?>> filtersByName = filtersByTypeAndName.get(type);
        if (filtersByName == null) {
            return null;
        }
        return filtersByName.get(name);
    }
}
