/*
 * Copyright 2019 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.netflix.zuul.filters.FilterType;
import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;


/**
 * Unit tests for {@link FilterVerifier}.
 */
@RunWith(JUnit4.class)
public class FilterVerifierTest {

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
