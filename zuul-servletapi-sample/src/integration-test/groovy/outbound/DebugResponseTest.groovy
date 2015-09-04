package outbound

import com.netflix.config.DynamicBooleanProperty
import com.netflix.zuul.context.SessionContext
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
class DebugResponseTest extends GroovyTestCase {

    @Mock
    HttpRequestMessage request

    SessionContext context
    HttpResponseMessage response

    @Before
    public void setup() {
        context = new SessionContext()
        Mockito.when(request.getContext()).thenReturn(context)
        response = new HttpResponseMessageImpl(context, request, 99)
    }

    @Test
    public void test() {

        DebugResponse filter = new DebugResponse()
        filter.INCLUDE_DEBUG_HEADER = Mockito.mock(DynamicBooleanProperty.class)
        Mockito.when(filter.INCLUDE_DEBUG_HEADER.get()).thenReturn(true)

        filter.applyAsync(response).toBlocking().single()

        assertEquals("", response.getHeaders().getFirst("X-Zuul-Debug-Header"))
    }
}
