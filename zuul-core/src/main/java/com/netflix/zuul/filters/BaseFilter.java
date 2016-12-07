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
package com.netflix.zuul.filters;

import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.zuul.message.ZuulMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Base abstract class for ZuulFilters. The base class defines abstract methods to define:
 * filterType() - to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
 * "route" for routing to an origin, "post" for post-routing filters, "error" for error handling.
 * We also support a "static" type for static responses see  StaticResponseFilter.
 * Any filterType made be created or added and run by calling FilterProcessor.runFilters(type)
 * <p/>
 * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
 * important for a filter. filterOrders do not need to be sequential.
 * <p/>
 * ZuulFilters may be disabled using Archaius Properties.
 * <p/>
 * By default ZuulFilters are static; they don't carry state. This may be overridden by overriding the isStaticFilter() property to false
 *
 * @author Mikey Cohen
 *         Date: 10/26/11
 *         Time: 4:29 PM
 */
public abstract class BaseFilter<I extends ZuulMessage, O extends ZuulMessage> implements ZuulFilter<I,O>
{
    private final CachedDynamicBooleanProperty filterDisabled = new CachedDynamicBooleanProperty(disablePropertyName(), false);

    @Override
    public String filterName() {
        return this.getClass().getName();
    }

    @Override
    public boolean overrideStopFilterProcessing()
    {
        return false;
    }

    /**
     * The name of the Archaius property to disable this filter. by default it is zuul.[classname].[filtertype].disable
     *
     * @return
     */
    public String disablePropertyName() {
        return "zuul." + this.getClass().getSimpleName() + "." + filterType().toString() + ".disable";
    }

    /**
     * If true, the filter has been disabled by archaius and will not be run
     *
     * @return
     */
    @Override
    public boolean isDisabled() {
        return filterDisabled.get();
    }

    @Override
    public ZuulMessage getDefaultOutput(I input)
    {
        return input;
    }

    @Override
    public FilterSyncType getSyncType()
    {
        return FilterSyncType.ASYNC;
    }

    @Override
    public String toString()
    {
        return String.valueOf(filterType()) + ":" + String.valueOf(filterName());
    }

    public static class TestUnit {
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
}
