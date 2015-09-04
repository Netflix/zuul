package inbound

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestMessage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static org.mockito.Mockito.when

@RunWith(MockitoJUnitRunner.class)
class DebugRequestTest extends GroovyTestCase {

    @Mock
    HttpRequestMessage response
    @Mock
    HttpRequestMessage request

    SessionContext context

    @Before
    public void setup() {
        context = new SessionContext()
        when(request.getContext()).thenReturn(context)
        when(request.getInboundRequest()).thenReturn(request)
    }

    @Test
    public void testDebug() {

        DebugRequest debugFilter = new DebugRequest()

        when(request.getClientIp()).thenReturn("1.1.1.1")
        when(request.getMethod()).thenReturn("method")
        when(request.getProtocol()).thenReturn("protocol")
        when(request.getPathAndQuery()).thenReturn("uri")

        Headers headers = new Headers()
        when(request.getHeaders()).thenReturn(headers)
        headers.add("Host", "moldfarm.com")
        headers.add("X-Forwarded-Proto", "https")

        context.setDebugRequest(true)
        debugFilter.applyAsync(request).toBlocking().first()

        ArrayList<String> debugList = com.netflix.zuul.context.Debug.getRequestDebug(context)

        Assert.assertEquals("REQUEST_INBOUND:: > LINE: METHOD uri protocol", debugList.get(0))
        Assert.assertEquals("REQUEST_INBOUND:: > HDR: X-Forwarded-Proto:https", debugList.get(1))
        Assert.assertEquals("REQUEST_INBOUND:: > HDR: Host:moldfarm.com", debugList.get(2))
    }
}
