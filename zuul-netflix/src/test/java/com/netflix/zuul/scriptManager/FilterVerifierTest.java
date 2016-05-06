package com.netflix.zuul.scriptManager;

import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FilterVerifierTest {

    String sGoodGroovyScriptFilter = "import com.netflix.zuul.ZuulFilter\n" +
            "import com.netflix.zuul.context.NFRequestContext\n" +
            "\n" +
            "class filter extends ZuulFilter {\n" +
            "\n" +
            "    String filterType() {\n" +
            "        return 'pre'\n" +
            "    }\n" +
            "\n" +
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

    String sNotZuulFilterGroovy = "import com.netflix.zuul.ZuulFilter\n" +
            "import com.netflix.zuul.context.NFRequestContext\n" +
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

    String sCompileFailCode = "import com.netflix.zuul.ZuulFilter\n" +
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
        Class filterClass = FilterVerifier.getInstance().compileGroovy(sGoodGroovyScriptFilter);
        assertNotNull(filterClass);
        filterClass = FilterVerifier.getInstance().compileGroovy(sNotZuulFilterGroovy);
        assertNotNull(filterClass);

        try {
            FilterVerifier.getInstance().compileGroovy(sCompileFailCode);
            fail(); //we shouldn't get here
        } catch (Exception ignored) {
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
                fail();
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
            fail();//we shouldn't get here
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        }

        filterClass = FilterVerifier.getInstance().compileGroovy(sNotZuulFilterGroovy);
        assertNotNull(filterClass);
        try {
            Object filter = FilterVerifier.getInstance().instanciateClass(filterClass);
            try {
                FilterVerifier.getInstance().checkZuulFilterInstance(filter);
                fail();//we shouldn't get here
            } catch (InstantiationException ignored) {
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        }
    }


    @Test
    public void testVerify() {

        try {
            FilterInfo filterInfo = FilterVerifier.getInstance().verifyFilter(sGoodGroovyScriptFilter);
            assertNotNull(filterInfo);
            assertEquals(filterInfo.getFilterID(), "null:filter:pre");
            assertEquals(filterInfo.getFilterType(), "pre");
            assertEquals(filterInfo.getFilterName(), "filter");
            assertFalse(filterInfo.isActive());
            assertFalse(filterInfo.isCanary());


        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        }

        try {
            FilterInfo filterInfo = FilterVerifier.getInstance().verifyFilter(sNotZuulFilterGroovy);
            fail();// shouldn't get here
        } catch (InstantiationException ignored) {

        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        }

        try {
            FilterInfo filterInfo = FilterVerifier.getInstance().verifyFilter(sCompileFailCode);
            fail();// shouldn't get here
        } catch (CompilationFailedException ignored) {

        } catch (InstantiationException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail(); //we shouldn't get here
        }

    }
}
