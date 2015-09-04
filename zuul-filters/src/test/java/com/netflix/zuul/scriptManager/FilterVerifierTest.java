package com.netflix.zuul.scriptManager;

import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;

public class FilterVerifierTest {

    String sGoodGroovyScriptFilter = "import com.netflix.zuul.filters.*\n" +
                                     "import com.netflix.zuul.context.*\n" +
                                     "import com.netflix.zuul.message.*\n" +
                                     "import com.netflix.zuul.message.http.*\n" +
                                     "\n" +
                                     "class filter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage> {\n" +
                                     "\n" +
                                     "    String filterType() {\n" +
                                     "        return 'in'\n" +
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
                                  "    String filterType() {\n" +
                                  "        return 'pre'\n" +
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
                              "    String filterType() {\n" +
                              "        return 'in'\n" +
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
        Class filterClass = FilterVerifier.getInstance().compileGroovy(sGoodGroovyScriptFilter);
        assertNotNull(filterClass);
        filterClass = FilterVerifier.getInstance().compileGroovy(sNotZuulFilterGroovy);
        assertNotNull(filterClass);

        try {
            filterClass = FilterVerifier.getInstance().compileGroovy(sCompileFailCode);
            assertFalse(true); //we shouldn't get here
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void testZuulFilterInstance() {
        Class filterClass = FilterVerifier.getInstance().compileGroovy(sGoodGroovyScriptFilter);
        assertNotNull(filterClass);
        try {
            Object filter = FilterVerifier.getInstance().instanciateClass(filterClass);
            try {
                FilterVerifier.getInstance().checkZuulFilterInstance(filter);
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

        filterClass = FilterVerifier.getInstance().compileGroovy(sNotZuulFilterGroovy);
        assertNotNull(filterClass);
        try {
            Object filter = FilterVerifier.getInstance().instanciateClass(filterClass);
            try {
                FilterVerifier.getInstance().checkZuulFilterInstance(filter);
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
            FilterInfo filterInfo = FilterVerifier.getInstance().verifyFilter(sGoodGroovyScriptFilter);
            assertNotNull(filterInfo);
            assertEquals(filterInfo.getFilterID(), "null:filter:in");
            assertEquals(filterInfo.getFilterType(), "in");
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
            FilterInfo filterInfo = FilterVerifier.getInstance().verifyFilter(sNotZuulFilterGroovy);
            assertFalse(true);// shouldn't get here
        } catch (InstantiationException e) {
            e.printStackTrace();
            assertTrue(true); //we shouldn't get here
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            assertFalse(true); //we shouldn't get here
        }

        try {
            FilterInfo filterInfo = FilterVerifier.getInstance().verifyFilter(sCompileFailCode);
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