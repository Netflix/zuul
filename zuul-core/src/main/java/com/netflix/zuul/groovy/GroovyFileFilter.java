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
package com.netflix.zuul.groovy;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 5/30/13
 * Time: 11:47 AM
 * To change this template use File | Settings | File Templates.
 */

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.FilenameFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Filters only .groovy files
 */
public class GroovyFileFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return name.endsWith(".groovy");
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        private File nonGroovyFile;
        @Mock
        private File groovyFile;

        @Mock
        private File directory;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }


        @Test
        public void testGroovyFileFilter() {

            when(nonGroovyFile.getName()).thenReturn("file.mikey");
            when(groovyFile.getName()).thenReturn("file.groovy");

            GroovyFileFilter filter = new GroovyFileFilter();

            assertFalse(filter.accept(nonGroovyFile, "file.mikey"));
            assertTrue(filter.accept(groovyFile, "file.groovy"));

        }
    }
}