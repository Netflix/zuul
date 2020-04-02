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

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StaticFilterLoaderTest {

    private static final FilterFactory factory = new DefaultFilterFactory();

    @Test
    public void getFiltersByType() {
        StaticFilterLoader filterLoader =
                new StaticFilterLoader(factory,
                        ImmutableSet.of(DummyFilter2.class, DummyFilter1.class, DummyFilter22.class));

        SortedSet<ZuulFilter<?, ?>> filters = filterLoader.getFiltersByType(FilterType.INBOUND);
        Truth.assertThat(filters).hasSize(3);
        List<ZuulFilter<?, ?>> filterList = new ArrayList<>(filters);

        Truth.assertThat(filterList.get(0)).isInstanceOf(DummyFilter1.class);
        Truth.assertThat(filterList.get(1)).isInstanceOf(DummyFilter2.class);
        Truth.assertThat(filterList.get(2)).isInstanceOf(DummyFilter22.class);
    }

    @Test
    public void getFilterByNameAndType() {
        StaticFilterLoader filterLoader =
                new StaticFilterLoader(factory, ImmutableSet.of(DummyFilter2.class, DummyFilter1.class));

        ZuulFilter<?, ?> filter = filterLoader.getFilterByNameAndType("Robin", FilterType.INBOUND);

        Truth.assertThat(filter).isInstanceOf(DummyFilter2.class);
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

    @Filter(order = 1, type = FilterType.INBOUND)
    static class DummyFilter22 extends HttpInboundSyncFilter {

        @Override
        public String filterName() {
            return "Williams";
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
