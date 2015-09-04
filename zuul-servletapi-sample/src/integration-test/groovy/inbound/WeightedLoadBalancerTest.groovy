package inbound

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.dependency.ribbon.RibbonConfig
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.class)
class WeightedLoadBalancerTest extends GroovyTestCase {

    WeightedLoadBalancer filter
    SessionContext ctx
    HttpResponseMessage response

    @Mock
    HttpRequestMessage request

    @Before
    public void setup() {
        filter = Mockito.spy(new WeightedLoadBalancer())
        ctx = new SessionContext()
        Mockito.when(request.getContext()).thenReturn(ctx)
        response = new HttpResponseMessageImpl(ctx, request, 99)
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
        Assert.assertTrue(ctx.routeVIP == "test")
        Assert.assertTrue(ctx.routeHost == null)
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
        Assert.assertTrue(ctx.routeVIP == null)
        Assert.assertTrue(ctx.routeHost != null)
    }
}
