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
package com.netflix.zuul;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Base abstract class for ZuulFilters. The base class defines abstract methods to define:
 * filterType() - to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
 * "route" for routing to an origin, "post" for post-routing com.netflix.zuul.filters, "error" for error handling.
 * We also support a "static" type for static responses see  StaticResponseFilter.
 * Any filterType made be created or added and run by calling com.netflix.zuul.FilterProcessor.runFilters(type)
 * <p/>
 * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
 * important for a filter. filterOrders do not need to be sequential.
 * <p/>
 * ZuulFilters may be disabled using Archius Properties.
 * <p/>
 * By default ZuulFilters are static; they don't carry state. This may be overridden by overriding the isStaticFilter() property to false
 *
 * @author Mikey Cohen
 *         Date: 10/26/11
 *         Time: 4:29 PM
 */
public class ZuulFilterTest {

    @Mock
    private ZuulFilter f1;
    @Mock
    private ZuulFilter f2;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSort() {

        when(f1.filterOrder()).thenReturn(1);
        when(f2.filterOrder()).thenReturn(10);
        when(f1.compareTo(any(ZuulFilter.class))).thenCallRealMethod();
        when(f2.compareTo(any(ZuulFilter.class))).thenCallRealMethod();

        ArrayList<ZuulFilter> list = new ArrayList<ZuulFilter>();
        list.add(f2);
        list.add(f1);

        Collections.sort(list);

        assertSame(f1, list.get(0));
    }

    @Test
    public void testShouldFilter() {
        class TestZuulFilter extends ZuulFilter {

            @Override
            public String filterType() {
                return null;
            }

            @Override
            public int filterOrder() {
                return 0;
            }

            public boolean shouldFilter() {
                return false;
            }

            public Object run() {
                return null;
            }
        }

        TestZuulFilter tf1 = spy(new TestZuulFilter());
        TestZuulFilter tf2 = spy(new TestZuulFilter());

        when(tf1.shouldFilter()).thenReturn(true);
        when(tf2.shouldFilter()).thenReturn(false);

        try {
            tf1.runFilter();
            tf2.runFilter();
            verify(tf1, times(1)).run();
            verify(tf2, times(0)).run();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }

    @Test
    public void testIsFilterDisabled() {
        class TestZuulFilter extends ZuulFilter {

            @Override
            public String filterType() {
                return null;
            }

            @Override
            public int filterOrder() {
                return 0;
            }

            public boolean isFilterDisabled() {
                return false;
            }

            public boolean shouldFilter() {
                return true;
            }

            public Object run() {
                return null;
            }
        }

        TestZuulFilter tf1 = spy(new TestZuulFilter());
        TestZuulFilter tf2 = spy(new TestZuulFilter());

        when(tf1.isFilterDisabled()).thenReturn(false);
        when(tf2.isFilterDisabled()).thenReturn(true);

        try {
            tf1.runFilter();
            tf2.runFilter();
            verify(tf1, times(1)).run();
            verify(tf2, times(0)).run();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }
}
