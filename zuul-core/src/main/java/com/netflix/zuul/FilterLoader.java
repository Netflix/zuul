/*
 * Copyright 2013 Netflix, Inc.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is one of the core classes in Zuul. It compiles, loads from a File, and checks if source code changed.
 * It also holds ZuulFilters by filterType.
 *
 * @author Mikey Cohen
 *         Date: 11/3/11
 *         Time: 1:59 PM
 */
public class FilterLoader {
    final static FilterLoader INSTANCE = new FilterLoader();

    private static final Logger LOG = LoggerFactory.getLogger(FilterLoader.class);

    private final ConcurrentHashMap<String, Long> filterClassLastModified = new ConcurrentHashMap<String, Long>();
    private final ConcurrentHashMap<String, String> filterClassCode = new ConcurrentHashMap<String, String>();
    private final ConcurrentHashMap<String, String> filterCheck = new ConcurrentHashMap<String, String>();
    private final ConcurrentHashMap<String, List<ZuulFilter>> hashFiltersByType = new ConcurrentHashMap<String, List<ZuulFilter>>();

    private FilterRegistry filterRegistry = FilterRegistry.instance();

    static DynamicCodeCompiler COMPILER;
    
    static FilterFactory FILTER_FACTORY = new DefaultFilterFactory();

    /**
     * Sets a Dynamic Code Compiler
     *
     * @param compiler
     */
    public void setCompiler(DynamicCodeCompiler compiler) {
        COMPILER = compiler;
    }

    // overidden by tests
    public void setFilterRegistry(FilterRegistry r) {
        this.filterRegistry = r;
    }

    /**
     * Sets a FilterFactory
     * 
     * @param factory
     */
    public void setFilterFactory(FilterFactory factory) {
        FILTER_FACTORY = factory;
    }
    
    /**
     * @return Singleton FilterLoader
     */
    public static FilterLoader getInstance() {
        return INSTANCE;
    }

    /**
     * Given source and name will compile and store the filter if it detects that the filter code has changed or
     * the filter doesn't exist. Otherwise it will return an instance of the requested ZuulFilter
     *
     * @param sCode source code
     * @param sName name of the filter
     * @return the ZuulFilter
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public ZuulFilter getFilter(String sCode, String sName) throws Exception {

        if (filterCheck.get(sName) == null) {
            filterCheck.putIfAbsent(sName, sName);
            if (!sCode.equals(filterClassCode.get(sName))) {
                LOG.info("reloading code " + sName);
                filterRegistry.remove(sName);
            }
        }
        ZuulFilter filter = filterRegistry.get(sName);
        if (filter == null) {
            Class clazz = COMPILER.compile(sCode, sName);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = (ZuulFilter) FILTER_FACTORY.newInstance(clazz);
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
     * @param file
     * @return true if the filter in file successfully read, compiled, verified and added to Zuul
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     */
    public boolean putFilter(File file) throws Exception {
        String sName = file.getAbsolutePath() + file.getName();
        if (filterClassLastModified.get(sName) != null && (file.lastModified() != filterClassLastModified.get(sName))) {
            LOG.debug("reloading filter " + sName);
            filterRegistry.remove(sName);
        }
        ZuulFilter filter = filterRegistry.get(sName);
        if (filter == null) {
            Class clazz = COMPILER.compile(file);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = (ZuulFilter) FILTER_FACTORY.newInstance(clazz);
                List<ZuulFilter> list = hashFiltersByType.get(filter.filterType());
                if (list != null) {
                    hashFiltersByType.remove(filter.filterType()); //rebuild this list
                }
                filterRegistry.put(file.getAbsolutePath() + file.getName(), filter);
                filterClassLastModified.put(sName, file.lastModified());
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a list of filters by the filterType specified
     *
     * @param filterType
     * @return a List<ZuulFilter>
     */
    public List<ZuulFilter> getFiltersByType(String filterType) {

        List<ZuulFilter> list = hashFiltersByType.get(filterType);
        if (list != null) return list;

        list = new ArrayList<ZuulFilter>();

        Collection<ZuulFilter> filters = filterRegistry.getAllFilters();
        for (Iterator<ZuulFilter> iterator = filters.iterator(); iterator.hasNext(); ) {
            ZuulFilter filter = iterator.next();
            if (filter.filterType().equals(filterType)) {
                list.add(filter);
            }
        }
        Collections.sort(list); // sort by priority

        hashFiltersByType.putIfAbsent(filterType, list);
        return list;
    }


    public static class TestZuulFilter extends ZuulFilter {

        public TestZuulFilter() {
            super();
        }

        @Override
        public String filterType() {
            return "test";
        }

        @Override
        public int filterOrder() {
            return 0;
        }

        public boolean shouldFilter() {
            return false;
        }

        public Object run() {
            return null;
        }
    }
}
