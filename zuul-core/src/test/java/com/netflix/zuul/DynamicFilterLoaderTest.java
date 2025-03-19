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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.MutableFilterRegistry;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

class DynamicFilterLoaderTest {

    private final FilterRegistry registry = new MutableFilterRegistry();

    private final FilterFactory filterFactory = new DefaultFilterFactory();

    private DynamicFilterLoader loader;

    @BeforeEach
    void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        loader = new DynamicFilterLoader(registry, filterFactory);
    }

    @Test
    void testPutFiltersForClasses() throws Exception {
        loader.putFiltersForClasses(new String[] {TestZuulFilter.class.getName()});

        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(1, filters.size());
    }

    @Test
    void testPutFiltersForClassesException() throws Exception {
        Exception caught = null;
        try {
            loader.putFiltersForClasses(new String[] {"asdf"});
        } catch (ClassNotFoundException e) {
            caught = e;
        }
        assertTrue(caught != null);
        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(0, filters.size());
    }

    @Test
    void testGetFiltersByType() throws Exception {
        loader.putFiltersForClasses(new String[] {TestZuulFilter.class.getName()});

        Collection<ZuulFilter<?, ?>> filters = registry.getAllFilters();
        assertEquals(1, filters.size());

        Collection<ZuulFilter<?, ?>> list = loader.getFiltersByType(FilterType.INBOUND);
        assertTrue(list != null);
        assertEquals(1, list.size());
        ZuulFilter<?, ?> filter = list.iterator().next();
        assertTrue(filter != null);
        assertEquals(FilterType.INBOUND, filter.filterType());
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
