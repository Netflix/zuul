package com.netflix.zuul.groovy;

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
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 11/3/11
 * Time: 1:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroovyLoader {
    final static GroovyLoader INSTANCE = new GroovyLoader();

    private static final Logger LOG = LoggerFactory.getLogger(GroovyLoader.class);

    ConcurrentHashMap<String, ProxyFilter> filterInstanceMap = new ConcurrentHashMap<String, ProxyFilter>();
    ConcurrentHashMap<String, Long> filterClassLastModified = new ConcurrentHashMap<String, Long>();
    ConcurrentHashMap<String, String> filterClassCode = new ConcurrentHashMap<String, String>();
    ConcurrentHashMap<String, String> filterCheck = new ConcurrentHashMap<String, String>();
    ConcurrentHashMap<String, List<ProxyFilter>> hashFiltersByType = new ConcurrentHashMap<String, List<ProxyFilter>>();


    public static GroovyLoader getInstance() {
        return INSTANCE;
    }

    public ProxyFilter getFilter(String sCode, String sName) throws IllegalAccessException, InstantiationException {

        if (filterCheck.get(sName) == null) {
            filterCheck.putIfAbsent(sName, sName);
            if (!sCode.equals(filterClassCode.get(sName))) {
                LOG.debug("reloading groovy code " + sName);
                filterInstanceMap.remove(sName);
            }
        }
        ProxyFilter filter = filterInstanceMap.get(sName);
        if (filter == null) {
            Class clazz = loadGroovyClass(sCode, sName);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = (ProxyFilter) clazz.newInstance();
                filterInstanceMap.putIfAbsent(sCode, filter);
            }
        }
        return filter;

    }

    public int filterInstanceMapSize() {
        return filterInstanceMap.size();
    }


    public boolean putFilter(File file) throws IllegalAccessException, InstantiationException, IOException {
        String sName = file.getAbsolutePath() + file.getName();
        if (filterClassLastModified.get(sName) != null && (file.lastModified() != filterClassLastModified.get(sName))) {
            LOG.debug("reloading groovy " + sName);
            filterInstanceMap.remove(sName);
        }
        ProxyFilter filter = filterInstanceMap.get(sName);
        if (filter == null) {
            Class clazz = loadGroovyClass(file);
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                filter = (ProxyFilter) clazz.newInstance();
                List<ProxyFilter> list = hashFiltersByType.get(filter.filterType());
                if (list != null) {
                    hashFiltersByType.remove(filter.filterType()); //rebuild this list
                }
                filterInstanceMap.put(file.getAbsolutePath() + file.getName(), filter);
                filterClassLastModified.put(sName, file.lastModified());
                return true;
            }
        }

        return false;
    }


    public List<ProxyFilter> getFiltersByType(String filterType) {

        List<ProxyFilter> list = hashFiltersByType.get(filterType);
        if (list != null) return list;

        list = new ArrayList<ProxyFilter>();

        Collection<ProxyFilter> filters = filterInstanceMap.values();
        for (Iterator<ProxyFilter> iterator = filters.iterator(); iterator.hasNext(); ) {
            ProxyFilter filter = iterator.next();
            if (filter.filterType().equals(filterType)) {
                list.add(filter);
            }
        }
        Collections.sort(list); // sort by priority

        hashFiltersByType.putIfAbsent(filterType, list);
        return list;
    }


    Class loadGroovyClass(String sCode, String sName) {

        ClassLoader parent = GroovyLoader.class.getClassLoader();
        GroovyClassLoader loader = new GroovyClassLoader(parent);
        LOG.warn("compiling filter: " + sName);
        Class groovyClass = loader.parseClass(sCode, sName);
        return groovyClass;
    }


    GroovyClassLoader getGroovyClassLoader() {
        ClassLoader parent = GroovyLoader.class.getClassLoader();
        return new GroovyClassLoader(parent);
    }


    Class loadGroovyClass(File file) throws IOException {
        GroovyClassLoader loader = getGroovyClassLoader();
        Class groovyClass = loader.parseClass(file);
        return groovyClass;
    }


    public void test() throws IOException, IllegalAccessException, InstantiationException {
        ClassLoader parent = getClass().getClassLoader();
        GroovyClassLoader loader = new GroovyClassLoader(parent);
        Class groovyClass = loader.parseClass(new File("src/test/groovy/script/HelloWorld.groovy"));

// let's call some method on an instance
        GroovyObject groovyObject = (GroovyObject) groovyClass.newInstance();
        Object[] args = {};
        groovyObject.invokeMethod("run", args);
    }

    public static class TestProxyFilter extends ProxyFilter {

        public TestProxyFilter() {
            super();
        }

        @Override
        public String filterType() {
            return "test";
        }

        @Override
        public int filterOrder() {
            return 0;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean shouldFilter() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Object run() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }


    public static class UnitTest {
        File file;

        @Test
        public void testGetFilterFromFile() {
            GroovyLoader loader = spy(GroovyLoader.getInstance());
            file = mock(File.class);

            try {
                doReturn(TestProxyFilter.class).when(loader).loadGroovyClass(file);
                assertTrue(loader.putFilter(file));
                assertTrue(loader.filterInstanceMapSize() == 1);

            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }


        @Test
        public void testGetFiltersByType() {
            GroovyLoader loader = spy(GroovyLoader.getInstance());

            try {
                List<ProxyFilter> list = loader.getFiltersByType("test");
                assertTrue(list != null);
                assertTrue(list.size() == 1);
                ProxyFilter filter = list.get(0);
                assertTrue(filter != null);
                assertTrue(filter.filterType().equals("test"));
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }


        @Test
        public void testGetFilterFromString() {

            GroovyLoader loader = spy(GroovyLoader.getInstance());

            try {

                String string = "";
                doReturn(TestProxyFilter.class).when(loader).loadGroovyClass(string, string);
                ProxyFilter filter = loader.getFilter(string, string);

                assertNotNull(filter);
                assertTrue(filter.getClass() == TestProxyFilter.class);
                assertTrue(loader.filterInstanceMapSize() == 2);


            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }


        @Test
        public void testLoadGroovyFromString() {

            GroovyLoader loader = spy(GroovyLoader.getInstance());
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

//            GroovyLoader loader = spy(GroovyLoader.getInstance());
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
