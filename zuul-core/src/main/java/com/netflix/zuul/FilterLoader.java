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
import com.netflix.zuul.groovy.GroovyCompiler;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is one of the core classes in Zuul. It compiles, loads from a File, and checks if source code changed.
 * It also holds ZuulFilters by filterType.
 *
 * @author Mikey Cohen
 *         Date: 11/3/11
 *         Time: 1:59 PM
 */
@Singleton
public class FilterLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(FilterLoader.class);

    private final ConcurrentHashMap<String, Long> filterClassLastModified = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> filterClassCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> filterCheck = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<FilterType, List<ZuulFilter<?, ?>>> hashFiltersByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ZuulFilter<?, ?>> filtersByNameAndType = new ConcurrentHashMap<>();

    private final FilterRegistry filterRegistry;

    private final DynamicCodeCompiler compiler;
    
    private final FilterFactory filterFactory;

    public FilterLoader() {
        this(new FilterRegistry(), new GroovyCompiler(), new DefaultFilterFactory());
    }

    @Inject
    public FilterLoader(FilterRegistry filterRegistry, DynamicCodeCompiler compiler, FilterFactory filterFactory) {
        this.filterRegistry = filterRegistry;
        this.compiler = compiler;
        this.filterFactory = filterFactory;
    }

    /**
     * Given source and name will compile and store the filter if it detects that the filter code has changed or
     * the filter doesn't exist. Otherwise it will return an instance of the requested ZuulFilter
     *
     * @param sCode source code
     * @param sName name of the filter
     * @return the IZuulFilter
     */
    public ZuulFilter<?, ?> getFilter(String sCode, String sName) throws Exception {
        if (filterCheck.get(sName) == null) {
            filterCheck.putIfAbsent(sName, sName);
            if (!sCode.equals(filterClassCode.get(sName))) {
                LOG.info("reloading code " + sName);
                filterRegistry.remove(sName);
            }
        }
        ZuulFilter<?, ?> filter = filterRegistry.get(sName);
        if (filter == null) {
            Class<?> clazz = compiler.compile(sCode, sName);
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
    public boolean putFilter(File file) {
        try {
            String sName = file.getAbsolutePath();
            if (filterClassLastModified.get(sName) != null
                    && (file.lastModified() != filterClassLastModified.get(sName))) {
                LOG.debug("reloading filter " + sName);
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
            LOG.error("Error loading filter! Continuing. file=" + file, e);
            return false;
        }

        return false;
    }

    private void putFilter(String sName, ZuulFilter<?, ?> filter, long lastModified) {
        List<ZuulFilter<?, ?>> list = hashFiltersByType.get(filter.filterType());
        if (list != null) {
            hashFiltersByType.remove(filter.filterType()); //rebuild this list
        }

        String nameAndType = filter.filterType() + ":" + filter.filterName();
        filtersByNameAndType.put(nameAndType, filter);

        filterRegistry.put(sName, filter);
        filterClassLastModified.put(sName, lastModified);
    }

    /**
     * Load and cache filters by className
     *
     * @param classNames The class names to load
     * @return List of the loaded filters
     * @throws Exception If any specified filter fails to load, this will abort. This is a safety mechanism so we can
     * prevent running in a partially loaded state.
     */
    public List<ZuulFilter<?, ?>> putFiltersForClasses(String[] classNames) throws Exception {
        List<ZuulFilter<?, ?>> newFilters = new ArrayList<>();
        for (String className : classNames) {
            newFilters.add(putFilterForClassName(className));
        }
        return Collections.unmodifiableList(newFilters);
    }

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
    public List<ZuulFilter<?, ?>> getFiltersByType(FilterType filterType) {
        List<ZuulFilter<?, ?>> list = hashFiltersByType.get(filterType);
        if (list != null) return list;

        list = new ArrayList<>();

        for (ZuulFilter<?, ?> filter : filterRegistry.getAllFilters()) {
            if (filter.filterType().equals(filterType)) {
                list.add(filter);
            }
        }

        // Sort by filterOrder.
        list.sort(Comparator.comparingInt(ZuulFilter::filterOrder));

        hashFiltersByType.putIfAbsent(filterType, list);
        return Collections.unmodifiableList(list);
    }

    public ZuulFilter<?, ?> getFilterByNameAndType(String name, FilterType type)
    {
        if (name == null || type == null)
            return null;

        String nameAndType = type.toString() + ":" + name;
        return filtersByNameAndType.get(nameAndType);
    }
}
