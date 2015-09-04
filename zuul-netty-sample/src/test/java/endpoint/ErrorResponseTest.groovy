package endpoint

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.message.http.HttpQueryParams
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import com.netflix.zuul.monitoring.MonitoringHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.class)
class ErrorResponseTest extends GroovyTestCase {
    ErrorResponse filter
    SessionContext ctx
    Throwable th

    @Mock
    HttpRequestMessage request

    HttpQueryParams queryParams

    @Before
    public void setup() {
        MonitoringHelper.initMocks()
        filter = new ErrorResponse()
        ctx = new SessionContext()
        Mockito.when(request.getContext()).thenReturn(ctx)

        queryParams = new HttpQueryParams()
        Mockito.when(request.getQueryParams()).thenReturn(queryParams)
    }

    @Test
    public void testApply()
    {
        th = new ZuulException("test", "a-cause")
        ctx.setError(th)

        HttpResponseMessageImpl response = filter.apply(request)
        assertEquals(500, response.getStatus())
        assertEquals("test", new String(response.getBody(), "UTF-8"))
    }
}
