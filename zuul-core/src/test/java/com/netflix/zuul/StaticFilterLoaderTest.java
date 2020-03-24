/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StaticFilterLoaderTest {


    private static final FilterFactory factory = new DefaultFilterFactory();

    @Test
    public void getFiltersByType() {

        StaticFilterLoader filterLoader =
                new StaticFilterLoader(factory, Arrays.asList(DummyFilter2.class, DummyFilter1.class));

        List<ZuulFilter<?, ?>> filters = filterLoader.getFiltersByType(FilterType.INBOUND);
        assertEquals(2, filters.size());
        // Filters are sorted by order
        assertEquals(DummyFilter1.class, filters.get(0).getClass());
        assertEquals(DummyFilter2.class, filters.get(1).getClass());
    }

    @Test
    public void getFilterByNameAndType() {
        StaticFilterLoader filterLoader =
                new StaticFilterLoader(factory, Arrays.asList(DummyFilter2.class, DummyFilter1.class));

        ZuulFilter<?, ?> filter = filterLoader.getFilterByNameAndType("Robin", FilterType.INBOUND);

        assertEquals(DummyFilter2.class, filter.getClass());
    }

    @Filter(order = 0, type = FilterType.INBOUND)
    static class DummyFilter1 extends HttpInboundSyncFilter {

        @Override
        public String filterName() {
            return "Batman";
        }

        @Override
        public int filterOrder() {
            return 0;
        }

        @Override
        public boolean shouldFilter(HttpRequestMessage msg) {
            return true;
        }

        @Override
        public HttpRequestMessage apply(HttpRequestMessage input) {
            return input;
        }
    }

    @Filter(order = 1, type = FilterType.INBOUND)
    static class DummyFilter2 extends HttpInboundSyncFilter {

        @Override
        public String filterName() {
            return "Robin";
        }

        @Override
        public int filterOrder() {
            return 1;
        }

        @Override
        public boolean shouldFilter(HttpRequestMessage msg) {
            return true;
        }

        @Override
        public HttpRequestMessage apply(HttpRequestMessage input) {
            return input;
        }
    }
}
