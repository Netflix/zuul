package outbound

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import com.netflix.zuul.stats.StatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.class)
class StatsTest extends GroovyTestCase {

    Stats filter
    SessionContext ctx
    HttpResponseMessage response
    Headers reqHeaders

    @Mock
    HttpRequestMessage request

    @Before
    public void setup() {
        filter = new Stats()
        ctx = new SessionContext()

        Mockito.when(request.getContext()).thenReturn(ctx)
        Mockito.when(request.getInboundRequest()).thenReturn(request)

        reqHeaders = new Headers()
        Mockito.when(request.getHeaders()).thenReturn(reqHeaders)

        response = new HttpResponseMessageImpl(ctx, request, 99)
    }

    @Test
    public void testHeaderResponse() {

        Assert.assertTrue(filter.filterType().equals("out"))

        ctx.route = "testStats"
        filter.apply(response)

        Assert.assertTrue(StatsManager.manager.getRouteStatusCodeMonitor("testStats", 99) != null)
    }
}
