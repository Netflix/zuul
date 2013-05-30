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
package com.netflix.zuul.groovy;

import com.netflix.zuul.filters.FilterRegistry;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import org.junit.Test;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

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

    private final FilterRegistry filterRegistry = FilterRegistry.instance();

//    ConcurrentHashMap<String, ZuulFilter> filterInstanceMap = new ConcurrentHashMap<String, ZuulFilter>();
    ConcurrentHashMap<String, Long> filterClassLastModified = new ConcurrentHashMap<String, Long>();
    ConcurrentHashMap<String, String> filterClassCode = new ConcurrentHashMap<String, String>();
    ConcurrentHashMap<String, String> filterCheck = new ConcurrentHashMap<String, String>();
    ConcurrentHashMap<String, List<ZuulFilter>> hashFiltersByType = new ConcurrentHashMap<String, List<ZuulFilter>>();

    /**
     * @return Singleton FilterLoader
     */
    public static FilterLoader getInstance() {
        return INSTANCE;
    }

    /**
     * Given groovy source and name will compile and store the filter if it detects that the filter code has changed or
     * the filter doesn't exist. Otherwise it will return an instance of the requested ZuulFilter
     *
     * @param sCode Groovy source code
     * @param sName name of the filter
     * @return the ZuulFilter
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public ZuulFilter getFilter(String sCode, String sName) throws IllegalAccessException, InstantiationException {

        if (filterCheck.get(sName) == null) {
            filterCheck.putIfAbsent(sName, sName);
            if (!sCode.equals(filterClassCode.get(sName))) {
                LOG.info("reloading groovy code " + sName);
                filterRegistry.remove(sName);
            }
        }
        ZuulFilter filter = filterRegistry.get(sName);
        if (filter == null) {
            Class clazz = loadGroovyClass(sCode, sName);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = (ZuulFilter) clazz.newInstance();
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
     * From a file this will read the Groovy ZuulFilter source code, compile it, and add it to the list of current filters
     * a true response means that it was successful.
     *
     * @param file
     * @return true if the filter in file successfully read, compiled, verified and added to Zuul
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     */
    public boolean putFilter(File file) throws IllegalAccessException, InstantiationException, IOException {
        String sName = file.getAbsolutePath() + file.getName();
        if (filterClassLastModified.get(sName) != null && (file.lastModified() != filterClassLastModified.get(sName))) {
            LOG.debug("reloading groovy " + sName);
            filterRegistry.remove(sName);
        }
        ZuulFilter filter = filterRegistry.get(sName);
        if (filter == null) {
            Class clazz = loadGroovyClass(file);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = (ZuulFilter) clazz.newInstance();
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

    /**
     * Compiles Groovy code and returns the Class of the compiles code.
     *
     * @param sCode
     * @param sName
     * @return
     */
    Class loadGroovyClass(String sCode, String sName) {

        ClassLoader parent = FilterLoader.class.getClassLoader();
        GroovyClassLoader loader = getGroovyClassLoader();
        LOG.warn("Compiling filter: " + sName);
        Class groovyClass = loader.parseClass(sCode, sName);
        return groovyClass;
    }

    /**
     * @return a new GroovyClassLoader
     */
    GroovyClassLoader getGroovyClassLoader() {
        ClassLoader parent = FilterLoader.class.getClassLoader();
        return new GroovyClassLoader();
    }

    /**
     * Compiles groovy class from a file
     *
     * @param file
     * @return
     * @throws IOException
     */
    Class loadGroovyClass(File file) throws IOException {
        GroovyClassLoader loader = getGroovyClassLoader();
        Class groovyClass = loader.parseClass(file);
        return groovyClass;
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


    public static class UnitTest {
        File file;

        @Test
        public void testGetFilterFromFile() {
            FilterLoader loader = spy(FilterLoader.getInstance());
            file = mock(File.class);

            try {
                doReturn(TestZuulFilter.class).when(loader).loadGroovyClass(file);
                assertTrue(loader.putFilter(file));
                assertTrue(loader.filterInstanceMapSize() == 1);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }


        @Test
        public void testGetFiltersByType() {
            FilterLoader loader = spy(FilterLoader.getInstance());

            try {
                List<ZuulFilter> list = loader.getFiltersByType("test");
                assertTrue(list != null);
                assertTrue(list.size() == 1);
                ZuulFilter filter = list.get(0);
                assertTrue(filter != null);
                assertTrue(filter.filterType().equals("test"));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }


        @Test
        public void testGetFilterFromString() {

            FilterLoader loader = spy(FilterLoader.getInstance());

            try {

                String string = "";
                doReturn(TestZuulFilter.class).when(loader).loadGroovyClass(string, string);
                ZuulFilter filter = loader.getFilter(string, string);

                assertNotNull(filter);
                assertTrue(filter.getClass() == TestZuulFilter.class);
                assertTrue(loader.filterInstanceMapSize() == 1);


            } catch (Exception e) {
                e.printStackTrace();
            }

        }


        @Test
        public void testLoadGroovyFromString() {

            FilterLoader loader = spy(FilterLoader.getInstance());
            try {

                String code = "class test { public String hello(){return \"hello\" } } ";
                Class clazz = loader.loadGroovyClass(code, "test");
                assertNotNull(clazz);
                assertEquals(clazz.getName(), "test");
                GroovyObject groovyObject = (GroovyObject) clazz.newInstance();
                Object[] args = {};
                String s = (String) groovyObject.invokeMethod("hello", args);
                assertEquals(s, "hello");


            } catch (Exception e) {
                assertFalse(true);
            }

        }


        @Test
        public void testLoadGroovyFromFile() {

//            FilterLoader loader = spy(FilterLoader.getInstance());
//            try {
//
//                String code = "class test { public String hello(){return \"hello\" } } ";
//                Class  clazz = loader.loadGroovyClass(file)
//                assertNotNull(clazz);
//                assertEquals(clazz.getName(), "test");
//
//            } catch (Exception e) {
//                assertFalse(true);
//            }

        }


    }


}
