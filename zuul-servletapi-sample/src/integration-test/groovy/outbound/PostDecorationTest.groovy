package outbound

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static org.mockito.Mockito.*
import static org.mockito.Mockito.spy

@RunWith(MockitoJUnitRunner.class)
class PostDecorationTest extends GroovyTestCase {

    PostDecoration filter
    SessionContext ctx
    HttpResponseMessage response
    Headers reqHeaders

    @Mock
    HttpRequestMessage request

    @Before
    public void setup() {
        filter = spy(new PostDecoration())
        ctx = new SessionContext()

        when(request.getContext()).thenReturn(ctx)
        when(request.getInboundRequest()).thenReturn(request)

        reqHeaders = new Headers()
        when(request.getHeaders()).thenReturn(reqHeaders)

        response = new HttpResponseMessageImpl(ctx, request, 99)
    }

    @Test
    public void testHeaderResponse() {

        filter.apply(response)

        Headers respHeaders = response.getHeaders()

        Assert.assertTrue(respHeaders.contains("X-Zuul", "zuul"))
        Assert.assertTrue(respHeaders.contains("X-Zuul-Instance", "unknown"))
        Assert.assertTrue(respHeaders.contains("Connection", "keep-alive"))

        Assert.assertEquals("out", filter.filterType())
        Assert.assertTrue(filter.shouldFilter(response))
    }
}
