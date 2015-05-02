package filters.post

import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static junit.framework.Assert.assertEquals

/**
 * User: michaels@netflix.com
 * Date: 5/1/15
 * Time: 5:35 PM
 */
class DebugResponse extends ZuulFilter
{
    static DynamicBooleanProperty INCLUDE_DEBUG_HEADER =
            DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_INCLUDE_DEBUG_HEADER, false);

    @Override
    String filterType() {
        return 'post'
    }

    @Override
    int filterOrder() {
        return 10000
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return Debug.debugRequest(ctx)

    }

    @Override
    SessionContext apply(SessionContext ctx)
    {
        HttpResponseMessage response = ctx.getResponse()

        if (INCLUDE_DEBUG_HEADER.get()) {
            String debugHeader = ""
            List<String> rd = (List<String>) ctx.getAttributes().get("routingDebug");
            rd?.each {
                debugHeader += "[[[${it}]]]";
            }
            response.getHeaders().set("X-Zuul-Debug-Header", debugHeader)
        }

        if (Debug.debugRequest(ctx)) {
            response.getHeaders().entries()?.each { Map.Entry<String, String> it ->
                Debug.addRequestDebug("OUTBOUND: <  " + it.key + ":" + it.value)
            }
        }

        return ctx
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {
        @Mock
        HttpRequestMessage request

        SessionContext context
        HttpResponseMessage response

        @Before
        public void setup() {
            response = new HttpResponseMessage(200)
            context = new SessionContext(request, response)
        }

        @Test
        public void test() {

            DebugResponse filter = new DebugResponse()
            filter.INCLUDE_DEBUG_HEADER = Mockito.mock(DynamicBooleanProperty.class)
            Mockito.when(filter.INCLUDE_DEBUG_HEADER.get()).thenReturn(true)

            filter.apply(context)

            assertEquals("", response.getHeaders().getFirst("X-Zuul-Debug-Header"))
        }
    }
}
