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
package pre

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Attributes
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.dependency.ribbon.RibbonConfig
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

/**
 * @author Mikey Cohen
 * Date: 7/9/12
 * Time: 1:19 PM
 */

class WeightedLoadBalancer extends ZuulFilter {
    DynamicStringProperty AltVIP = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_VIP, null)
    DynamicStringProperty AltHost = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_HOST, null)
    DynamicIntProperty AltPercent = DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_PERMYRIAD, 0)   //0-10000 is 0-100% of traffic
    DynamicIntProperty AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_MAXLIMIT, 500)

    Random rand = new Random()

    String filterType() {
        return "pre"
    }

    @Override
    int filterOrder() {
        return 30
    }

    /**
     * returns true if a randomValue is less than the zuul.router.alt.route.permyriad property value
     * @return
     */
    @Override
    boolean shouldFilter(SessionContext ctx) {

        if (AltPercent.get() == 0) return false
        if (ctx.getAttributes().getRouteHost() != null) return false //host calls are not going to be loaltPad calculated here.
        if (ctx.getAttributes().sendZuulResponse == false) return false;
        if (AltPercent.get() > AltPercentMaxLimit.get()) return false

        int randomValue = rand.nextInt(10000)
        return randomValue <= AltPercent.get()
    }

    @Override
    SessionContext apply(SessionContext ctx) {

        Attributes attrs = ctx.getAttributes()

        if (AltVIP.get() != null) {
            attrs.routeVIP = AltVIP.get()
            if (attrs.getRouteVIP().startsWith(RibbonConfig.getApplicationName())) {
                attrs.zuulToZuul = true // for zuulToZuul load testing
            }
        }
        if (AltHost.get() != null) {
            try {
                attrs.routeHost = new URL(AltHost.get())
                attrs.routeVIP = null

            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        return ctx
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        WeightedLoadBalancer filter
        SessionContext ctx
        HttpResponseMessage response
        Throwable th

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = Mockito.spy(new WeightedLoadBalancer())
            response = new HttpResponseMessage(99)
            ctx = new SessionContext(request, response)
            RibbonConfig.setApplicationName("zuul")
        }

        @Test
        public void testFalseRouting() {
            filter.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(filter.AltPercent.get()).thenReturn(new Integer(0))
            Assert.assertFalse(filter.shouldFilter(ctx))
        }

        @Test
        public void testMaxLimit() {
            filter.AltPercent = DynamicPropertyFactory.getInstance().getIntProperty("y", 10000)

            filter.AltVIP = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(filter.AltVIP.get()).thenReturn("test")
            Assert.assertFalse(filter.shouldFilter(ctx))
        }

        @Test
        public void testTrueRouting() {
            filter.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(filter.AltPercent.get()).thenReturn(new Integer(10000))
            filter.AltVIP = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(filter.AltVIP.get()).thenReturn("test")
            filter.AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty("x", 100000)
            Assert.assertTrue(filter.shouldFilter(ctx))
            filter.apply(ctx)
            Assert.assertTrue(ctx.getAttributes().routeVIP == "test")
            Assert.assertTrue(ctx.getAttributes().routeHost == null)
        }

        @Test
        public void testTrueHostRouting() {
            filter.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(filter.AltPercent.get()).thenReturn(new Integer(10000))
            filter.AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty("x", 100000)

            filter.AltHost = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(filter.AltHost.get()).thenReturn("http://www.moldfarm.com")
            Assert.assertTrue(filter.shouldFilter(ctx))
            filter.apply(ctx)
            Assert.assertTrue(ctx.getAttributes().routeVIP == null)
            Assert.assertTrue(ctx.getAttributes().routeHost != null)
        }

    }

}
