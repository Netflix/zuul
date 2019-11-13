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
package com.netflix.zuul.filters;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.netflix.zuul.message.ZuulMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link BaseFilter}.   Currently named BaseFilter2Test as there is an existing
 * class named BaseFilterTest.
 *
 * TODO(carl-mastrangelo): refactor {@link BaseFilterTest} to not conflict with this class.
 */
@RunWith(JUnit4.class)
public class BaseFilter2Test {

    @Mock
    private BaseFilter f1;
    @Mock
    private BaseFilter f2;
    @Mock
    private ZuulMessage req;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testShouldFilter() {
        class TestZuulFilter extends BaseSyncFilter
        {
            @Override
            public int filterOrder() {
                return 0;
            }

            @Override
            public FilterType filterType() {
                return FilterType.INBOUND;
            }

            @Override
            public boolean shouldFilter(ZuulMessage req) {
                return false;
            }

            @Override
            public ZuulMessage apply(ZuulMessage req) {
                return null;
            }
        }

        TestZuulFilter tf1 = spy(new TestZuulFilter());
        TestZuulFilter tf2 = spy(new TestZuulFilter());

        when(tf1.shouldFilter(req)).thenReturn(true);
        when(tf2.shouldFilter(req)).thenReturn(false);
    }
}
