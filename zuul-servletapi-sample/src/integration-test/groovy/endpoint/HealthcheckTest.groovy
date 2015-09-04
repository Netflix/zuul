package endpoint

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.class)
class HealthcheckTest extends GroovyTestCase {
    Healthcheck filter
    SessionContext ctx

    @Mock
    HttpRequestMessage request

    @Before
    public void setup() {
        filter = new Healthcheck()
        ctx = new SessionContext()
        Mockito.when(request.getContext()).thenReturn(ctx)
    }

    @Test
    public void testHealthcheck() {

        Mockito.when(request.getPath()).thenReturn("/healthcheck")
        Assert.assertTrue(filter.shouldFilter(request))

        HttpResponseMessage response = filter.apply(request)

        Assert.assertTrue(response.getBody() != null)
        String bodyStr = new String(response.getBody(), "UTF-8")
        Assert.assertTrue(bodyStr.contains("<health>ok</health>"))
    }
}
