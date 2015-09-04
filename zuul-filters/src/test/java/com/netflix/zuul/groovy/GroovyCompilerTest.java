package com.netflix.zuul.groovy;

import groovy.lang.GroovyObject;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;

public class GroovyCompilerTest {
    @Test
    public void testLoadGroovyFromString() {

        GroovyCompiler compiler = spy(new GroovyCompiler());

        try {

            String code = "class test { public String hello(){return \"hello\" } } ";
            Class clazz = compiler.compile(code, "test");
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
}