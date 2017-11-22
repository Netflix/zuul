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

import com.netflix.zuul.filters.*;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.groovy.GroovyCompiler;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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

    private final ConcurrentHashMap<String, Long> filterClassLastModified = new ConcurrentHashMap<String, Long>();
    private final ConcurrentHashMap<String, String> filterClassCode = new ConcurrentHashMap<String, String>();
    private final ConcurrentHashMap<String, String> filterCheck = new ConcurrentHashMap<String, String>();
    private final ConcurrentHashMap<FilterType, List<ZuulFilter>> hashFiltersByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ZuulFilter> filtersByNameAndType = new ConcurrentHashMap<>();

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
            Class clazz = compiler.compile(sCode, sName);
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
     * @param file
     * @return true if the filter in file successfully read, compiled, verified and added to Zuul
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     */
    public boolean putFilter(File file) throws Exception
    {
        try {
            String sName = file.getAbsolutePath();
            if (filterClassLastModified.get(sName) != null && (file.lastModified() != filterClassLastModified.get(sName))) {
                LOG.debug("reloading filter " + sName);
                filterRegistry.remove(sName);
            }
            ZuulFilter filter = filterRegistry.get(sName);
            if (filter == null) {
                Class clazz = compiler.compile(file);
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    filter = filterFactory.newInstance(clazz);
                    putFilter(sName, filter, file.lastModified());
                    return true;
                }
            }
        }
        catch (Exception e) {
            LOG.error("Error loading filter! Continuing. file=" + String.valueOf(file), e);
            return false;
        }

        return false;
    }

    void putFilter(String sName, ZuulFilter filter, long lastModified)
    {
        List<ZuulFilter> list = hashFiltersByType.get(filter.filterType());
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
    public List<ZuulFilter> putFiltersForClasses(String[] classNames) throws Exception
    {
        List<ZuulFilter> newFilters = new ArrayList<>();
        for (String className : classNames)
        {
            newFilters.add(putFilterForClassName(className));
        }
        return newFilters;
    }

    public ZuulFilter putFilterForClassName(String className) throws Exception
    {
        Class clazz = Class.forName(className);
        if (! ZuulFilter.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Specified filter class does not implement ZuulFilter interface!");
        }
        else {
            ZuulFilter filter = filterFactory.newInstance(clazz);
            putFilter(className, filter, System.currentTimeMillis());
            return filter;
        }
    }

    /**
     * Returns a list of filters by the filterType specified
     *
     * @param filterType
     * @return a List<ZuulFilter>
     */
    public List<ZuulFilter> getFiltersByType(FilterType filterType) {

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

        // Sort by filterOrder.
        Collections.sort(list, new Comparator<ZuulFilter>() {
            @Override
            public int compare(ZuulFilter o1, ZuulFilter o2) {
                return o1.filterOrder() - o2.filterOrder();
            }
        });

        hashFiltersByType.putIfAbsent(filterType, list);
        return list;
    }

    public ZuulFilter getFilterByNameAndType(String name, FilterType type)
    {
        if (name == null || type == null)
            return null;

        String nameAndType = type.toString() + ":" + name;
        return filtersByNameAndType.get(nameAndType);
    }


    public static class TestZuulFilter extends BaseSyncFilter {

        public TestZuulFilter() {
            super();
        }

        @Override
        public FilterType filterType() {
            return FilterType.INBOUND;
        }

        @Override
        public int filterOrder() {
            return 0;
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return false;
        }

        @Override
        public ZuulMessage apply(ZuulMessage msg) {
            return null;
        }
    }


    public static class UnitTest {

        @Mock
        File file;

        @Mock
        DynamicCodeCompiler compiler;

        @Mock
        FilterRegistry registry;

        FilterFactory filterFactory = new DefaultFilterFactory();

        FilterLoader loader;

        TestZuulFilter filter = new TestZuulFilter();

        @Before
        public void before() throws Exception
        {
            MockitoAnnotations.initMocks(this);

            loader = spy(new FilterLoader(registry, compiler, filterFactory));

            doReturn(TestZuulFilter.class).when(compiler).compile(file);
            when(file.getAbsolutePath()).thenReturn("/filters/in/SomeFilter.groovy");
        }

        @Test
        public void testGetFilterFromFile() throws Exception {
            assertTrue(loader.putFilter(file));
            verify(registry).put(any(String.class), any(BaseFilter.class));
        }

        @Test
        public void testPutFiltersForClasses() throws Exception {
            loader.putFiltersForClasses(new String[]{TestZuulFilter.class.getName()});
            verify(registry).put(any(String.class), any(BaseFilter.class));
        }

        @Test
        public void testPutFiltersForClassesException() throws Exception {
            Exception caught = null;
            try {
                loader.putFiltersForClasses(new String[]{"asdf"});
            }
            catch (ClassNotFoundException e) {
                caught = e;
            }
            assertTrue(caught != null);
            verify(registry, times(0)).put(any(String.class), any(BaseFilter.class));
        }

        @Test
        public void testGetFiltersByType() throws Exception {
            assertTrue(loader.putFilter(file));

            verify(registry).put(any(String.class), any(ZuulFilter.class));

            final List<ZuulFilter> filters = new ArrayList<ZuulFilter>();
            filters.add(filter);
            when(registry.getAllFilters()).thenReturn(filters);

            List<ZuulFilter> list = loader.getFiltersByType(FilterType.INBOUND);
            assertTrue(list != null);
            assertTrue(list.size() == 1);
            ZuulFilter filter = list.get(0);
            assertTrue(filter != null);
            assertTrue(filter.filterType().equals(FilterType.INBOUND));
        }


        @Test
        public void testGetFilterFromString() throws Exception {
            String string = "";
            doReturn(TestZuulFilter.class).when(compiler).compile(string, string);
            ZuulFilter filter = loader.getFilter(string, string);

            assertNotNull(filter);
            assertTrue(filter.getClass() == TestZuulFilter.class);
//            assertTrue(loader.filterInstanceMapSize() == 1);
        }


    }


}
