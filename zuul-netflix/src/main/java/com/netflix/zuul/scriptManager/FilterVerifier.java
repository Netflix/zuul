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
package com.netflix.zuul.scriptManager;

import com.netflix.zuul.groovy.ZuulFilter;
import com.netflix.zuul.ZuulApplicationInfo;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;

/**
 * @author Mikey Cohen
 * Date: 6/12/12
 * Time: 7:12 PM
 */
public class FilterVerifier {
    private static final FilterVerifier INSTANCE = new FilterVerifier();

    public static FilterVerifier getInstance() {
        return INSTANCE;
    }


    public FilterInfo verifyFilter(String sFilterCode) throws org.codehaus.groovy.control.CompilationFailedException, IllegalAccessException, InstantiationException {
        Class groovyClass = compileGroovy(sFilterCode);
        Object instance = instanciateClass(groovyClass);
        checkProxyFilterInstance(instance);
        ZuulFilter filter = (ZuulFilter) instance;


        String filter_id = FilterInfo.buildFilterID(ZuulApplicationInfo.getApplicationName(), filter.filterType(), groovyClass.getSimpleName());

        return new FilterInfo(filter_id, sFilterCode, filter.filterType(), groovyClass.getSimpleName(), filter.disablePropertyName(), "" +filter.filterOrder(), ZuulApplicationInfo.getApplicationName());
    }

    Object instanciateClass(Class groovyClass) throws InstantiationException, IllegalAccessException {
        return groovyClass.newInstance();
    }

    void checkProxyFilterInstance(Object proxyFilter) throws InstantiationException {
        if (!(proxyFilter instanceof ZuulFilter)) {
            throw new InstantiationException("Code is not a ZuulFilter Class ");
        }
    }

    public Class compileGroovy(String sFilterCode) throws org.codehaus.groovy.control.CompilationFailedException {
        GroovyClassLoader loader = new GroovyClassLoader();
        return loader.parseClass(sFilterCode);
    }


    public static class UnitTest {

        String sGoodGroovyScriptFilter = "import com.netflix.zuul.groovy.ZuulFilter\n" +
                "import com.netflix.zuul.context.NFRequestContext\n" +
                "\n" +
                "class filter extends ZuulFilter {\n" +
                "\n" +
                "    @Override\n" +
                "    String filterType() {\n" +
                "        return 'pre'\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    int filterOrder() {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    boolean shouldFilter() {\n" +
                "        return true\n" +
                "    }\n" +
                "\n" +
                "    Object run() {\n" +
                "        return null\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}";

        String sNotProxyFilterGroovy = "import com.netflix.zuul.groovy.ZuulFilter\n" +
                "import com.netflix.zuul.context.NFRequestContext\n" +
                "\n" +
                "class filter  {\n" +
                "\n" +
                "    @Override\n" +
                "    String filterType() {\n" +
                "        return 'pre'\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    int filterOrder() {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    boolean shouldFilter() {\n" +
                "        return true\n" +
                "    }\n" +
                "\n" +
                "    Object run() {\n" +
                "        return null\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}";

        String sCompileFailCode = "import com.netflix.zuul.groovy.ZuulFilter\n" +
                "import com.netflix.zuul.context.NFRequestContext\n" +
                "\n" +
                "cclass filter extends ZuulFilter {\n" +
                "\n" +
                "    @Override\n" +
                "    String filterType() {\n" +
                "        return 'pre'\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    int filterOrder() {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    boolean shouldFilter() {\n" +
                "        return true\n" +
                "    }\n" +
                "\n" +
                "    Object run() {\n" +
                "        return null\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}";


        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testCompile() {
            Class filterClass = FilterVerifier.INSTANCE.compileGroovy(sGoodGroovyScriptFilter);
            assertNotNull(filterClass);
            filterClass = FilterVerifier.INSTANCE.compileGroovy(sNotProxyFilterGroovy);
            assertNotNull(filterClass);

            try {
                filterClass = FilterVerifier.INSTANCE.compileGroovy(sCompileFailCode);
                assertFalse(true); //we shouldn't get here
            } catch (Exception e) {
                assertTrue(true);
            }
        }

        @Test
        public void testProxyFilterInstance() {
            Class filterClass = FilterVerifier.INSTANCE.compileGroovy(sGoodGroovyScriptFilter);
            assertNotNull(filterClass);
            try {
                Object filter = FilterVerifier.INSTANCE.instanciateClass(filterClass);
                try {
                    FilterVerifier.INSTANCE.checkProxyFilterInstance(filter);
                } catch (InstantiationException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    assertFalse(true); //we shouldn't get here
                }
            } catch (InstantiationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            }

            filterClass = FilterVerifier.INSTANCE.compileGroovy(sNotProxyFilterGroovy);
            assertNotNull(filterClass);
            try {
                Object filter = FilterVerifier.INSTANCE.instanciateClass(filterClass);
                try {
                    FilterVerifier.INSTANCE.checkProxyFilterInstance(filter);
                    assertFalse(true); //we shouldn't get here
                } catch (InstantiationException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    assertTrue(true); //this
                }
            } catch (InstantiationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            }
        }


        @Test
        public void testVerify() {

            try {
                FilterInfo filterInfo = FilterVerifier.INSTANCE.verifyFilter(sGoodGroovyScriptFilter);
                assertNotNull(filterInfo);
                assertEquals(filterInfo.getFilterID(), "null:filter:pre");
                assertEquals(filterInfo.getFilterType(), "pre");
                assertEquals(filterInfo.getFilterName(), "filter");
                assertFalse(filterInfo.isActive());
                assertFalse(filterInfo.isCanary());


            } catch (InstantiationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            }

            try {
                FilterInfo filterInfo = FilterVerifier.INSTANCE.verifyFilter(sNotProxyFilterGroovy);
                assertFalse(true);// shouldn't get here
            } catch (InstantiationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertTrue(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            }

            try {
                FilterInfo filterInfo = FilterVerifier.INSTANCE.verifyFilter(sCompileFailCode);
                assertFalse(true);// shouldn't get here
            } catch (CompilationFailedException e) {
                assertTrue(true); //we shouldn't get here
            } catch (InstantiationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                assertFalse(true); //we shouldn't get here
            }

        }


    }

}


