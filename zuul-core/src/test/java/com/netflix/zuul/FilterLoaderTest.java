package com.netflix.zuul;/*
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

import com.netflix.zuul.filters.FilterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class is one of the core classes in Zuul. It compiles, loads from a File, and checks if source code changed.
 * It also holds ZuulFilters by filterType.
 *
 * @author Mikey Cohen
 *         Date: 11/3/11
 *         Time: 1:59 PM
 */
public class FilterLoaderTest {

    @Mock
    File file;

    @Mock
    DynamicCodeCompiler compiler;

    @Mock
    FilterRegistry registry;

    FilterLoader loader;

    FilterLoader.TestZuulFilter filter = new FilterLoader.TestZuulFilter();

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        loader = spy(new FilterLoader());
        loader.setCompiler(compiler);
        loader.setFilterRegistry(registry);
    }

    @Test
    public void testGetFilterFromFile() throws Exception {
        doReturn(FilterLoader.TestZuulFilter.class).when(compiler).compile(file);
        assertTrue(loader.putFilter(file));
        verify(registry).put(any(String.class), any(ZuulFilter.class));
    }

    @Test
    public void testGetFiltersByType() throws Exception {
        doReturn(FilterLoader.TestZuulFilter.class).when(compiler).compile(file);
        assertTrue(loader.putFilter(file));

        verify(registry).put(any(String.class), any(ZuulFilter.class));

        final List<ZuulFilter> filters = new ArrayList<ZuulFilter>();
        filters.add(filter);
        when(registry.getAllFilters()).thenReturn(filters);

        List<ZuulFilter> list = loader.getFiltersByType("test");
        assertTrue(list != null);
        assertTrue(list.size() == 1);
        ZuulFilter filter = list.get(0);
        assertTrue(filter != null);
        assertTrue(filter.filterType().equals("test"));
    }


    @Test
    public void testGetFilterFromString() throws Exception {
        String string = "";
        doReturn(FilterLoader.TestZuulFilter.class).when(compiler).compile(string, string);
        ZuulFilter filter = loader.getFilter(string, string);

        assertNotNull(filter);
        assertTrue(filter.getClass() == FilterLoader.TestZuulFilter.class);
    }

}
