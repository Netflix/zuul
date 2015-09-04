package endpoint

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

import static org.mockito.Mockito.when

class OptionsTest extends GroovyTestCase {

    Options filter
    SessionContext ctx

    @Mock
    HttpRequestMessage request

    @Before
    public void setup() {
        filter = new Options()
        ctx = new SessionContext()
        when(request.getContext()).thenReturn(ctx)
    }

    @Test
    public void testClientAccessPolicy() {

        when(request.getPath()).thenReturn("/anything")
        when(request.getMethod()).thenReturn("OPTIONS")
        assertTrue(filter.shouldFilter(request))

        HttpResponseMessage response = filter.apply(request)

        assertTrue(response.getBody() == null)
    }
}
