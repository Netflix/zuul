package com.netflix.api.proxy.groovy;

import com.netflix.api.proxy.monitoring.Tracer;
import com.netflix.api.proxy.monitoring.TracerFactory;
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
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/26/11
 * Time: 4:29 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ProxyFilter implements IProxyFilter, Comparable<ProxyFilter> {

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
            t = TracerFactory.instance().startMicroTracer("API_PROXY::" + this.getClass().getSimpleName());
            try {
                return run();
            } catch (Throwable e) {
                t.setName("API_PROXY::" + this.getClass().getSimpleName() + " failed");
                throw e;
            } finally {
                t.stopAndLog();
            }
        }
        return null;
    }

    public int compareTo(ProxyFilter proxyFilter) {
        return this.filterOrder() - proxyFilter.filterOrder();
    }

    public static class TestUnit {
        @Mock
        private ProxyFilter f1;
        @Mock
        private ProxyFilter f2;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testSort() {

            when(f1.filterOrder()).thenReturn(1);
            when(f2.filterOrder()).thenReturn(10);

            ArrayList<ProxyFilter> list = new ArrayList<ProxyFilter>();
            list.add(f1);
            list.add(f2);

            Collections.sort(list);

            assertTrue(list.get(0) == f1);

        }

        @Test
        public void testShouldFilter() {
            class TestProxyFilter extends ProxyFilter {

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

            TestProxyFilter tf1 = spy(new TestProxyFilter());
            TestProxyFilter tf2 = spy(new TestProxyFilter());

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
