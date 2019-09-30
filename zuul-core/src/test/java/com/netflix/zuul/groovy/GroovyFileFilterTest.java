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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link GroovyFileFilter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GroovyFileFilterTest {

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
