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
package com.netflix.zuul.groovy;

import com.netflix.zuul.monitoring.Tracer;
import com.netflix.zuul.monitoring.TracerFactory;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mikey Cohen
 * Date: 10/26/11
 * Time: 4:29 PM
 */
public abstract class ZuulFilter implements IZuulFilter, Comparable<ZuulFilter> {

    abstract public String filterType();

    abstract public int filterOrder();

    private final DynamicBooleanProperty filterDisabled =
        DynamicPropertyFactory.getInstance().getBooleanProperty(disablePropertyName(), false);

    public String disablePropertyName(){
        return "zuul." + this.getClass().getSimpleName() + "." + filterType() +".disable";
    }

    public Object runFilter() throws Throwable {
        if(filterDisabled.get()) return null;
        Tracer t;
        if (shouldFilter()) {
            t = TracerFactory.instance().startMicroTracer("ZUUL::" + this.getClass().getSimpleName());
            try {
                return run();
            } catch (Throwable e) {
                t.setName("ZUUL::" + this.getClass().getSimpleName() + " failed");
                throw e;
            } finally {
                t.stopAndLog();
            }
        }
        return null;
    }

    public int compareTo(ZuulFilter proxyFilter) {
        return this.filterOrder() - proxyFilter.filterOrder();
    }

    public static class TestUnit {
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

            ArrayList<ZuulFilter> list = new ArrayList<ZuulFilter>();
            list.add(f1);
            list.add(f2);

            Collections.sort(list);

            assertTrue(list.get(0) == f1);

        }

        @Test
        public void testShouldFilter() {
            class TestZuulFilter extends ZuulFilter {

                @Override
                public String filterType() {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public int filterOrder() {
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean shouldFilter() {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public Object run() {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
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
                throwable.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }

    }
}
