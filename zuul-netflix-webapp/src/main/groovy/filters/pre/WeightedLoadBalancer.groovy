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
package filters.pre

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.dependency.ribbon.RibbonConfig
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

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
    String envRegion = System.getenv("EC2_REGION");

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
    boolean shouldFilter() {


        if (AltPercent.get() == 0) return false
        if (AltVIP.get() == null && AltHost.get() == null) return false
        if (NFRequestContext.currentContext.host != null) return false //host calls are not going to be loaltPad calculated here.
        if (RequestContext.currentContext.sendZuulResponse == false) return false;
        if (AltPercent.get() > AltPercentMaxLimit.get()) return false

        int randomValue = rand.nextInt(10000)
        return randomValue <= AltPercent.get()
    }

    @Override
    Object run() {
        if (AltVIP.get() != null) {
            (NFRequestContext.currentContext).routeVIP = AltVIP.get()
            if (NFRequestContext.currentContext.routeVIP.startsWith(RibbonConfig.getApplicationName())) {
                NFRequestContext.getCurrentContext().zuulToZuul = true // for zuulToZuul load testing
            }
            return true
        }
        if (AltHost.get() != null) {
            try {
                (NFRequestContext.currentContext).host = new URL(AltHost.get())
                (NFRequestContext.currentContext).routeVIP = null

            } catch (Exception e) {
                e.printStackTrace()
                return false;
            }
            return true
        }

    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response

        @Mock
        HttpServletRequest request

        RequestContext ctx;

        @Before
        public void before() {
            // not sure why, but I need to call this explicitly
            RequestContext.setContextClass(NFRequestContext.class);

            MockitoAnnotations.initMocks(this);
            RequestContext.currentContext.unset()

            ctx = RequestContext.currentContext
            ctx.request = request
            ctx.response = response
        }

        @Test
        public void testFalseRouting() {
            request = Mockito.mock(HttpServletRequest.class)
            WeightedLoadBalancer weightedLoadBalancer = new WeightedLoadBalancer()
            weightedLoadBalancer = Mockito.spy(weightedLoadBalancer)
            weightedLoadBalancer.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(weightedLoadBalancer.AltPercent.get()).thenReturn(new Integer(0))
            Assert.assertFalse(weightedLoadBalancer.shouldFilter())
        }

        @Test
        public void testMaxLimit() {
            request = Mockito.mock(HttpServletRequest.class)
            WeightedLoadBalancer weightedLoadBalancer = new WeightedLoadBalancer()
            weightedLoadBalancer = Mockito.spy(weightedLoadBalancer)
            weightedLoadBalancer.AltPercent = DynamicPropertyFactory.getInstance().getIntProperty("y", 10000)

            weightedLoadBalancer.AltVIP = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(weightedLoadBalancer.AltVIP.get()).thenReturn("test")
            Assert.assertFalse(weightedLoadBalancer.shouldFilter())
        }

        @Test
        public void testTrueRouting() {
            request = Mockito.mock(HttpServletRequest.class)
            WeightedLoadBalancer weightedLoadBalancer = new WeightedLoadBalancer()
            weightedLoadBalancer = Mockito.spy(weightedLoadBalancer)
            weightedLoadBalancer.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(weightedLoadBalancer.AltPercent.get()).thenReturn(new Integer(10000))
            weightedLoadBalancer.AltVIP = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(weightedLoadBalancer.AltVIP.get()).thenReturn("test")
            weightedLoadBalancer.AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty("x", 100000)
            Assert.assertTrue(weightedLoadBalancer.shouldFilter())
            weightedLoadBalancer.run()
            Assert.assertTrue(NFRequestContext.currentContext.routeVIP == "test")
            Assert.assertTrue(NFRequestContext.currentContext.host == null)
        }

//        @Test
        public void testPercentRouting() {
            WeightedLoadBalancer weightedLoadBalancer = new WeightedLoadBalancer()
            weightedLoadBalancer.AltPercent = DynamicPropertyFactory.getInstance().getIntProperty("x", 100)
            weightedLoadBalancer.AltVIP = DynamicPropertyFactory.getInstance().getStringProperty("y", "test")
            weightedLoadBalancer.envRegion = "us-east-1"

            int nCount = 0

            for (int i = 0; i < 1000; ++i) {
                if (weightedLoadBalancer.shouldFilter()) nCount++
            }
            println(nCount)
            Assert.assertTrue(nCount > 8)
            Assert.assertTrue(nCount < 20)

        }

        @Test
        public void testTrueHostRouting() {
            request = Mockito.mock(HttpServletRequest.class)
            WeightedLoadBalancer weightedLoadBalancer = new WeightedLoadBalancer()
            weightedLoadBalancer = Mockito.spy(weightedLoadBalancer)
            weightedLoadBalancer.AltPercent = Mockito.mock(DynamicIntProperty.class)
            Mockito.when(weightedLoadBalancer.AltPercent.get()).thenReturn(new Integer(10000))
            weightedLoadBalancer.AltPercentMaxLimit = DynamicPropertyFactory.getInstance().getIntProperty("x", 100000)

            weightedLoadBalancer.AltHost = Mockito.mock(DynamicStringProperty.class)
            Mockito.when(weightedLoadBalancer.AltHost.get()).thenReturn("http://www.moldfarm.com")
            Assert.assertTrue(weightedLoadBalancer.shouldFilter())
            weightedLoadBalancer.run()
            Assert.assertTrue(NFRequestContext.currentContext.routeVIP == null)
            Assert.assertTrue(NFRequestContext.currentContext.host != null)
        }

    }

}
