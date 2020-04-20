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

import com.google.errorprone.annotations.DoNotCall;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An immutable static collection of filters.
 */
public final class StaticFilterLoader implements FilterLoader {

    private static final Logger logger = LoggerFactory.getLogger(StaticFilterLoader.class);

    public static final String RESOURCE_NAME = "META-INF/zuul/allfilters";

    private final Map<FilterType, ? extends SortedSet<ZuulFilter<?, ?>>> filtersByType;
    private final Map<FilterType, ? extends Map<String, ZuulFilter<?, ?>>> filtersByTypeAndName;

    @Inject
    public StaticFilterLoader(
            FilterFactory filterFactory, Set<? extends Class<? extends ZuulFilter<?, ?>>> filterTypes) {
        Map<FilterType, SortedSet<ZuulFilter<?, ?>>> filtersByType = new EnumMap<>(FilterType.class);
        Map<FilterType, Map<String, ZuulFilter<?, ?>>> filtersByName = new EnumMap<>(FilterType.class);
        for (Class<? extends ZuulFilter<?, ?>> clz : filterTypes) {
            try {
                ZuulFilter<?, ?> f = filterFactory.newInstance(clz);
                filtersByType.computeIfAbsent(f.filterType(), k -> new TreeSet<>(FILTER_COMPARATOR)).add(f);
                filtersByName.computeIfAbsent(f.filterType(), k -> new HashMap<>()).put(f.filterName(), f);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (Entry<FilterType, SortedSet<ZuulFilter<?, ?>>> entry : filtersByType.entrySet()) {
            entry.setValue(Collections.unmodifiableSortedSet(entry.getValue()));
        }
        Map<FilterType, Map<String, ZuulFilter<?, ?>>> immutableFiltersByName = new EnumMap<>(FilterType.class);
        for (Entry<FilterType, Map<String, ZuulFilter<?, ?>>> entry : filtersByName.entrySet()) {
            immutableFiltersByName.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        this.filtersByTypeAndName = Collections.unmodifiableMap(immutableFiltersByName);
        this.filtersByType = Collections.unmodifiableMap(filtersByType);
    }

    public static Set<Class<ZuulFilter<?, ?>>> loadFilterTypesFromResources(ClassLoader loader)
            throws IOException {
        Set<Class<ZuulFilter<?, ?>>> filterTypes = new LinkedHashSet<>();
        for (URL url : Collections.list(loader.getResources(RESOURCE_NAME))) {
            try (InputStream is = url.openStream();
                    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        Class<?> clz;
                        try {
                            clz = Class.forName(trimmed, false, loader);
                        } catch (ClassNotFoundException e) {
                            // This can happen if a filter is deleted, but the annotation processor doesn't
                            // remove it from the list.   This is mainly a problem with IntelliJ, which
                            // forces append only annotation processors.  Incremental recompilation drops
                            // most of the classpath, making the processor unable to reconstruct the filter
                            // list.  To work around this problem, use the stale, cached filter list from
                            // the initial full compilation and add to it.  This makes incremental
                            // compilation work later, at the cost of polluting the filter list.  It's a
                            // better experience to log a warning (and do a clean build), than to
                            // mysteriously classes.

                            logger.warn("Missing Filter", e);
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Class<ZuulFilter<?, ?>> filterClz =
                                (Class<ZuulFilter<?, ?>>) clz.asSubclass(ZuulFilter.class);
                        filterTypes.add(filterClz);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(filterTypes);
    }

    @Override
    @DoNotCall
    public boolean putFilter(File file) {
        throw new UnsupportedOperationException();
    }

    @Override
    @DoNotCall
    public List<ZuulFilter<?, ?>> putFiltersForClasses(String[] classNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    @DoNotCall
    public ZuulFilter<?, ?> putFilterForClassName(String className) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SortedSet<ZuulFilter<?, ?>> getFiltersByType(FilterType filterType) {
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
