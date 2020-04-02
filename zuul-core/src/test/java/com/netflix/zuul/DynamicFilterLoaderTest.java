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
package com.netflix.zuul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.MutableFilterRegistry;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class DynamicFilterLoaderTest {

    @Mock
    private File file;

    @Mock
    private DynamicCodeCompiler compiler;

    private final FilterRegistry registry = new MutableFilterRegistry();

    private final FilterFactory filterFactory = new DefaultFilterFactory();

    private DynamicFilterLoader loader;

    private final TestZuulFilter filter = new TestZuulFilter();

    @Before
    public void before() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        loader = new DynamicFilterLoader(registry, compiler, filterFactory);

        doReturn(TestZuulFilter.class).when(compiler).compile(file);
        when(file.getAbsolutePath()).thenReturn("/filters/in/SomeFilter.groovy");
    }

    @Test
    public void testGetFilterFromFile() throws Exception {
        assertTrue(loader.putFilter(file));

        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(1, filters.size());
    }

    @Test
    public void testPutFiltersForClasses() throws Exception {
        loader.putFiltersForClasses(new String[]{TestZuulFilter.class.getName()});

        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(1, filters.size());
    }

    @Test
    public void testPutFiltersForClassesException() throws Exception {
        Exception caught = null;
        try {
            loader.putFiltersForClasses(new String[]{"asdf"});
        }
        catch (ClassNotFoundException e) {
            caught = e;
        }
        assertTrue(caught != null);
        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(0, filters.size());
    }

    @Test
    public void testGetFiltersByType() throws Exception {
        assertTrue(loader.putFilter(file));

        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(1, filters.size());

        Collection<ZuulFilter<?, ?>> list = loader.getFiltersByType(FilterType.INBOUND);
        assertTrue(list != null);
        assertTrue(list.size() == 1);
        ZuulFilter<?, ?> filter = list.iterator().next();
        assertTrue(filter != null);
        assertTrue(filter.filterType().equals(FilterType.INBOUND));
    }

    @Test
    public void testGetFilterFromString() throws Exception {
        String string = "";
        doReturn(TestZuulFilter.class).when(compiler).compile(string, string);
        ZuulFilter filter = loader.getFilter(string, string);

        assertNotNull(filter);
        assertTrue(filter.getClass() == TestZuulFilter.class);
//            assertTrue(loader.filterInstanceMapSize() == 1);
    }

    private static final class TestZuulFilter extends BaseSyncFilter {

        TestZuulFilter() {
            super();
        }

        @Override
        public FilterType filterType() {
            return FilterType.INBOUND;
        }

        @Override
        public int filterOrder() {
            return 0;
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return false;
        }

        @Override
        public ZuulMessage apply(ZuulMessage msg) {
            return null;
        }
    }
}
