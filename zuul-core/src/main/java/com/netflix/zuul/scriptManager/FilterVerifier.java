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
package com.netflix.zuul.scriptManager;

import com.netflix.zuul.ZuulApplicationInfo;
import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.FilterType;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;

/**
 * verifies that the given source code is compilable in Groovy, can be instanciated, and is a ZuulFilter type
 *
 * @author Mikey Cohen
 *         Date: 6/12/12
 *         Time: 7:12 PM
 */
public class FilterVerifier {
    private static final FilterVerifier INSTANCE = new FilterVerifier();

    /**
     * @return Singleton
     */
    public static FilterVerifier getInstance() {
        return INSTANCE;
    }

    /**
     * verifies compilation, instanciation and that it is a ZuulFilter
     *
     * @param sFilterCode
     * @return a FilterInfo object representing that code
     * @throws org.codehaus.groovy.control.CompilationFailedException
     *
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public FilterInfo verifyFilter(String sFilterCode) throws org.codehaus.groovy.control.CompilationFailedException, IllegalAccessException, InstantiationException {
        Class groovyClass = compileGroovy(sFilterCode);
        Object instance = instanciateClass(groovyClass);
        checkZuulFilterInstance(instance);
        BaseFilter filter = (BaseFilter) instance;


        String filter_id = FilterInfo.buildFilterID(ZuulApplicationInfo.getApplicationName(), filter.filterType(), groovyClass.getSimpleName());

        return new FilterInfo(filter_id, sFilterCode, filter.filterType(), groovyClass.getSimpleName(), filter.disablePropertyName(), "" + filter.filterOrder(), ZuulApplicationInfo.getApplicationName());
    }

    Object instanciateClass(Class groovyClass) throws InstantiationException, IllegalAccessException {
        return groovyClass.newInstance();
    }

    void checkZuulFilterInstance(Object zuulFilter) throws InstantiationException {
        if (!(zuulFilter instanceof BaseFilter)) {
            throw new InstantiationException("Code is not a ZuulFilter Class ");
        }
    }

    /**
     * compiles the Groovy source code
     *
     * @param sFilterCode
     * @return
     * @throws org.codehaus.groovy.control.CompilationFailedException
     *
     */
    public Class compileGroovy(String sFilterCode) throws org.codehaus.groovy.control.CompilationFailedException {
        GroovyClassLoader loader = new GroovyClassLoader();
        return loader.parseClass(sFilterCode);
    }


    public static class UnitTest {

        String sGoodGroovyScriptFilter = "import com.netflix.zuul.filters.*\n" +
                "import com.netflix.zuul.context.*\n" +
                "import com.netflix.zuul.message.*\n" +
                "import com.netflix.zuul.message.http.*\n" +
                "\n" +
                "class filter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage> {\n" +
                "\n" +
                "    FilterType filterType() {\n" +
                "        return FilterType.INBOUND\n" +
                "    }\n" +
                "\n" +
                "    int filterOrder() {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    boolean shouldFilter(HttpRequestMessage req) {\n" +
                "        return true\n" +
                "    }\n" +
                "\n" +
                "    HttpRequestMessage apply(HttpRequestMessage req) {\n" +
                "        return null\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}";

        String sNotZuulFilterGroovy = "import com.netflix.zuul.filters.*\n" +
                "import com.netflix.zuul.context.*\n" +
                "import com.netflix.zuul.message.*\n" +
                "import com.netflix.zuul.message.http.*\n" +
                "\n" +
                "class filter  {\n" +
                "\n" +
                "    FilterType filterType() {\n" +
                "        return FilterType.INBOUND\n" +
                "    }\n" +
                "\n" +
                "    int filterOrder() {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    boolean shouldFilter(SessionContext ctx) {\n" +
                "        return true\n" +
                "    }\n" +
                "\n" +
                "    SessionContext apply(SessionContext ctx) {\n" +
                "        return null\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}";

        String sCompileFailCode = "import com.netflix.zuul.filters.*\n" +
                "import com.netflix.zuul.context.*\n" +
                "import com.netflix.zuul.message.*\n" +
                "import com.netflix.zuul.message.http.*\n" +
                "\n" +
                "ccclass filter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage> {\n" +
                "\n" +
                "    FilterType filterType() {\n" +
                "        return FilterType.INBOUND\n" +
                "    }\n" +
                "\n" +
                "    int filterOrder() {\n" +
                "        return 1\n" +
                "    }\n" +
                "\n" +
                "    boolean shouldFilter(HttpRequestMessage req) {\n" +
                "        return true\n" +
                "    }\n" +
                "\n" +
                "    HttpRequestMessage apply(HttpRequestMessage req) {\n" +
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
            filterClass = FilterVerifier.INSTANCE.compileGroovy(sNotZuulFilterGroovy);
            assertNotNull(filterClass);

            try {
                filterClass = FilterVerifier.INSTANCE.compileGroovy(sCompileFailCode);
                assertFalse(true); //we shouldn't get here
            } catch (Exception e) {
                assertTrue(true);
            }
        }

        @Test
        public void testZuulFilterInstance() {
            Class filterClass = FilterVerifier.INSTANCE.compileGroovy(sGoodGroovyScriptFilter);
            assertNotNull(filterClass);
            try {
                Object filter = FilterVerifier.INSTANCE.instanciateClass(filterClass);
                try {
                    FilterVerifier.INSTANCE.checkZuulFilterInstance(filter);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                    assertFalse(true); //we shouldn't get here
                }
            } catch (InstantiationException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            }

            filterClass = FilterVerifier.INSTANCE.compileGroovy(sNotZuulFilterGroovy);
            assertNotNull(filterClass);
            try {
                Object filter = FilterVerifier.INSTANCE.instanciateClass(filterClass);
                try {
                    FilterVerifier.INSTANCE.checkZuulFilterInstance(filter);
                    assertFalse(true); //we shouldn't get here
                } catch (InstantiationException e) {
                    e.printStackTrace();
                    assertTrue(true); //this
                }
            } catch (InstantiationException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            }
        }


        @Test
        public void testVerify() {

            try {
                FilterInfo filterInfo = FilterVerifier.INSTANCE.verifyFilter(sGoodGroovyScriptFilter);
                assertNotNull(filterInfo);
                assertEquals(filterInfo.getFilterID(), "null:filter:in");
                assertEquals(filterInfo.getFilterType(), FilterType.INBOUND);
                assertEquals(filterInfo.getFilterName(), "filter");
                assertFalse(filterInfo.isActive());
                assertFalse(filterInfo.isCanary());


            } catch (InstantiationException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            }

            try {
                FilterInfo filterInfo = FilterVerifier.INSTANCE.verifyFilter(sNotZuulFilterGroovy);
                assertFalse(true);// shouldn't get here
            } catch (InstantiationException e) {
                e.printStackTrace();
                assertTrue(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            }

            try {
                FilterInfo filterInfo = FilterVerifier.INSTANCE.verifyFilter(sCompileFailCode);
                assertFalse(true);// shouldn't get here
            } catch (CompilationFailedException e) {
                assertTrue(true); //we shouldn't get here
            } catch (InstantiationException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                assertFalse(true); //we shouldn't get here
            }

        }


    }

}


