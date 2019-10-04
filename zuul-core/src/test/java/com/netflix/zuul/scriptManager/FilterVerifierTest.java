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
import static org.junit.Assert.assertThrows;

import com.netflix.zuul.filters.FilterType;
import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 * Unit tests for {@link FilterVerifier}.
 */
@RunWith(JUnit4.class)
public class FilterVerifierTest {

    private final String sGoodGroovyScriptFilter = "import com.netflix.zuul.filters.*\n" +
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

    private final String sNotZuulFilterGroovy = "import com.netflix.zuul.filters.*\n" +
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

    private final String sCompileFailCode = "import com.netflix.zuul.filters.*\n" +
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

    @Test
    public void testCompile() {
        Class<?> filterClass = FilterVerifier.INSTANCE.compileGroovy(sGoodGroovyScriptFilter);
        assertNotNull(filterClass);
        filterClass = FilterVerifier.INSTANCE.compileGroovy(sNotZuulFilterGroovy);
        assertNotNull(filterClass);

        assertThrows(CompilationFailedException.class, () -> FilterVerifier.INSTANCE.compileGroovy(sCompileFailCode));
    }

    @Test
    public void testZuulFilterInstance() throws Exception {
        Class<?> filterClass = FilterVerifier.INSTANCE.compileGroovy(sGoodGroovyScriptFilter);
        assertNotNull(filterClass);

        Object filter1 = FilterVerifier.INSTANCE.instanciateClass(filterClass);
        FilterVerifier.INSTANCE.checkZuulFilterInstance(filter1);

        filterClass = FilterVerifier.INSTANCE.compileGroovy(sNotZuulFilterGroovy);
        assertNotNull(filterClass);

        Object filter2 = FilterVerifier.INSTANCE.instanciateClass(filterClass);
        assertThrows(InstantiationException.class, () -> FilterVerifier.INSTANCE.checkZuulFilterInstance(filter2));
    }

    @Test
    public void testVerify() throws Exception {
        FilterInfo filterInfo1 = FilterVerifier.INSTANCE.verifyFilter(sGoodGroovyScriptFilter);
        assertNotNull(filterInfo1);
        assertEquals(filterInfo1.getFilterID(), "null:filter:in");
        assertEquals(filterInfo1.getFilterType(), FilterType.INBOUND);
        assertEquals(filterInfo1.getFilterName(), "filter");
        assertFalse(filterInfo1.isActive());
        assertFalse(filterInfo1.isCanary());

        assertThrows(InstantiationException.class, () -> FilterVerifier.INSTANCE.verifyFilter(sNotZuulFilterGroovy));

        assertThrows(CompilationFailedException.class, () -> FilterVerifier.INSTANCE.verifyFilter(sCompileFailCode));
    }
}
