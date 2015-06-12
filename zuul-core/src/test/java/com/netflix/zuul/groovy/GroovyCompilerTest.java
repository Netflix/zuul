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

import groovy.lang.GroovyObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.spy;


@RunWith(MockitoJUnitRunner.class)
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

