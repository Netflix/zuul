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

import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class DynamicFilterLoader implements FilterLoader {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicFilterLoader.class);

    private final ConcurrentMap<String, Long> filterClassLastModified = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> filterClassCode = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> filterCheck = new ConcurrentHashMap<>();
    private final ConcurrentMap<FilterType, SortedSet<ZuulFilter<?, ?>>> hashFiltersByType = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZuulFilter<?, ?>> filtersByNameAndType = new ConcurrentHashMap<>();

    private final FilterRegistry filterRegistry;

    private final DynamicCodeCompiler compiler;

    private final FilterFactory filterFactory;

    @Inject
    public DynamicFilterLoader(
            FilterRegistry filterRegistry,
            DynamicCodeCompiler compiler,
            FilterFactory filterFactory) {
        this.filterRegistry = filterRegistry;
        this.compiler = compiler;
        this.filterFactory = filterFactory;
    }

    /**
     * Given source and name will compile and store the filter if it detects that the filter code
     * has changed or the filter doesn't exist. Otherwise it will return an instance of the
     * requested ZuulFilter.
     *
     * @deprecated it is unclear to me why this method is needed.   Nothing seems to use it, and the
     *             swapping of code seems to happen elsewhere.   This will be removed in a later
     *             Zuul release.
     */
    @Deprecated
    public ZuulFilter<?, ?> getFilter(String sourceCode, String filterName) throws Exception {
        if (filterCheck.get(filterName) == null) {
            filterCheck.putIfAbsent(filterName, filterName);
            if (!sourceCode.equals(filterClassCode.get(filterName))) {
                if (filterRegistry.isMutable()) {
                    LOG.info("reloading code {}", filterName);
                    filterRegistry.remove(filterName);
                } else {
                    LOG.warn("Filter registry is not mutable, discarding {}", filterName);
                }
            }
        }
        ZuulFilter<?, ?> filter = filterRegistry.get(filterName);
        if (filter == null) {
            Class<?> clazz = compiler.compile(sourceCode, filterName);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = filterFactory.newInstance(clazz);
            }
        }
        return filter;
    }

    /**
     * @return the total number of Zuul filters
     */
    public int filterInstanceMapSize() {
        return filterRegistry.size();
    }

    /**
     * From a file this will read the ZuulFilter source code, compile it, and add it to the list of current filters
     * a true response means that it was successful.
     *
     * @param file the file to load
     * @return true if the filter in file successfully read, compiled, verified and added to Zuul
     */
    @Override
    public boolean putFilter(File file) {
        if (!filterRegistry.isMutable()) {
            return false;
        }
        try {
            String sName = file.getAbsolutePath();
            if (filterClassLastModified.get(sName) != null
                    && (file.lastModified() != filterClassLastModified.get(sName))) {
                LOG.debug("reloading filter {}", sName);
                filterRegistry.remove(sName);
            }
            ZuulFilter<?, ?> filter = filterRegistry.get(sName);
            if (filter == null) {
                Class<?> clazz = compiler.compile(file);
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    filter = filterFactory.newInstance(clazz);
                    putFilter(sName, filter, file.lastModified());
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error loading filter! Continuing. file={}", file, e);
            return false;
        }

        return false;
    }

    private void putFilter(String filterName, ZuulFilter<?, ?> filter, long lastModified) {
        if (!filterRegistry.isMutable()) {
            LOG.warn("Filter registry is not mutable, discarding {}", filterName);
            return;
        }
        SortedSet<ZuulFilter<?, ?>> set = hashFiltersByType.get(filter.filterType());
        if (set != null) {
            hashFiltersByType.remove(filter.filterType()); //rebuild this list
        }

        String nameAndType = filter.filterType() + ":" + filter.filterName();
        filtersByNameAndType.put(nameAndType, filter);

        filterRegistry.put(filterName, filter);
        filterClassLastModified.put(filterName, lastModified);
    }

    /**
     * Load and cache filters by className
     *
     * @param classNames The class names to load
     * @return List of the loaded filters
     * @throws Exception If any specified filter fails to load, this will abort. This is a safety mechanism so we can
     * prevent running in a partially loaded state.
     */
    @Override
    public List<ZuulFilter<?, ?>> putFiltersForClasses(String[] classNames) throws Exception {
        List<ZuulFilter<?, ?>> newFilters = new ArrayList<>();
        for (String className : classNames) {
            newFilters.add(putFilterForClassName(className));
        }
        return Collections.unmodifiableList(newFilters);
    }

    @Override
    public ZuulFilter<?, ?> putFilterForClassName(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        if (!ZuulFilter.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Specified filter class does not implement ZuulFilter interface!");
        } else {
            ZuulFilter<?, ?> filter = filterFactory.newInstance(clazz);
            putFilter(className, filter, System.currentTimeMillis());
            return filter;
        }
    }

    /**
     * Returns a list of filters by the filterType specified
     */
    @Override
    public SortedSet<ZuulFilter<?, ?>> getFiltersByType(FilterType filterType) {
        SortedSet<ZuulFilter<?, ?>> set = hashFiltersByType.get(filterType);
        if (set != null) {
            return set;
        }

        set = new TreeSet<>(FILTER_COMPARATOR);

        for (ZuulFilter<?, ?> filter : filterRegistry.getAllFilters()) {
            if (filter.filterType().equals(filterType)) {
                set.add(filter);
            }
        }

        hashFiltersByType.putIfAbsent(filterType, set);
        return Collections.unmodifiableSortedSet(set);
    }

    @Override
    public ZuulFilter<?, ?> getFilterByNameAndType(String name, FilterType type) {
        if (name == null || type == null) {
            return null;
        }

        String nameAndType = type + ":" + name;
        return filtersByNameAndType.get(nameAndType);
    }
}
