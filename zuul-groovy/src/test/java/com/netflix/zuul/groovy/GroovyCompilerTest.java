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

package com.netflix.zuul.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import groovy.lang.GroovyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GroovyCompiler}.
 */
@ExtendWith(MockitoExtension.class)
class GroovyCompilerTest {

    @Test
    void testLoadGroovyFromString() {

        GroovyCompiler compiler = Mockito.spy(new GroovyCompiler());

        try {

            String code = "class test { public String hello(){return \"hello\" } } ";
            Class clazz = compiler.compile(code, "test");
            assertNotNull(clazz);
            assertEquals("test", clazz.getName());
            GroovyObject groovyObject = (GroovyObject) clazz.newInstance();
            Object[] args = {};
            String s = (String) groovyObject.invokeMethod("hello", args);
            assertEquals("hello", s);
        } catch (Exception e) {
            assertFalse(true);
        }
    }
}
