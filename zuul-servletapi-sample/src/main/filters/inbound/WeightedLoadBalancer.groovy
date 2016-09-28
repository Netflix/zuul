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
package inbound

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.dependency.ribbon.RibbonConfig
import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import com.netflix.zuul.message.http.HttpRequestMessage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

/**
 * @author Mikey Cohen
 * Date: 7/9/12
 * Time: 1:19 PM
 */

class WeightedLoadBalancer extends HttpInboundSyncFilter
{
    DynamicStringProperty AltVIP = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_VIP, null)
    DynamicStringProperty AltHost = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_HOST, null)
    DynamicIntProperty AltPercent = DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_PERMYRIAD, 0)   //0-10000 is 0-100% of traffic
    DynamicIntProperty AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_ROUTER_ALT_ROUTE_MAXLIMIT, 500)

    Random rand = new Random()

    @Override
    int filterOrder() {
        return 30
    }

    /**
     * returns true if a randomValue is less than the zuul.router.alt.route.permyriad property value
     * @return
     */
    @Override
    boolean shouldFilter(HttpRequestMessage request) {

        if (AltPercent.get() == 0) return false
        if (request.getContext().getRouteHost() != null) return false //host calls are not going to be loaltPad calculated here.
        if (request.getContext().sendZuulResponse == false) return false;
        if (AltPercent.get() > AltPercentMaxLimit.get()) return false

        int randomValue = rand.nextInt(10000)
        return randomValue <= AltPercent.get()
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {

        SessionContext context = request.getContext()

        if (AltVIP.get() != null) {
            context.routeVIP = AltVIP.get()
            if (context.getRouteVIP().startsWith(RibbonConfig.getApplicationName())) {
                context.zuulToZuul = true // for zuulToZuul load testing
            }
        }
        if (AltHost.get() != null) {
            try {
                context.routeHost = new URL(AltHost.get())
                context.routeVIP = null

            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        return request
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {

        WeightedLoadBalancer filter

        @Before
        public void setup() {
            super.setup()
            
            filter = new WeightedLoadBalancer()
            RibbonConfig.setApplicationName("zuul")
        }

        @Test
        public void testFalseRouting() {
            filter.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(filter.AltPercent.get()).thenReturn(new Integer(0))
            Assert.assertFalse(filter.shouldFilter(request))
        }

        @Test
        public void testMaxLimit() {
            filter.AltPercent = DynamicPropertyFactory.getInstance().getIntProperty("y", 10000)

            filter.AltVIP = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(filter.AltVIP.get()).thenReturn("test")
            Assert.assertFalse(filter.shouldFilter(request))
        }

        @Test
        public void testTrueRouting() {
            filter.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(filter.AltPercent.get()).thenReturn(new Integer(10000))
            filter.AltVIP = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(filter.AltVIP.get()).thenReturn("test")
            filter.AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty("x", 100000)
            Assert.assertTrue(filter.shouldFilter(request))
            filter.apply(request)
            Assert.assertTrue(context.routeVIP == "test")
            Assert.assertTrue(context.routeHost == null)
        }

        @Test
        public void testTrueHostRouting() {
            filter.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(filter.AltPercent.get()).thenReturn(new Integer(10000))
            filter.AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty("x", 100000)

            filter.AltHost = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(filter.AltHost.get()).thenReturn("http://www.moldfarm.com")
            Assert.assertTrue(filter.shouldFilter(request))
            filter.apply(request)
            Assert.assertTrue(context.routeVIP == null)
            Assert.assertTrue(context.routeHost != null)
        }

    }

}
