package filters.outbound

import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
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
class DebugResponse extends HttpOutboundSyncFilter
{
    static DynamicBooleanProperty INCLUDE_DEBUG_HEADER =
            DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_INCLUDE_DEBUG_HEADER, false);

    @Override
    int filterOrder() {
        return 10000
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return Debug.debugRequest(response.getContext())

    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response)
    {
        if (INCLUDE_DEBUG_HEADER.get()) {
            String debugHeader = ""
            List<String> rd = (List<String>) response.getContext().getAttributes().get("routingDebug");
            rd?.each {
                debugHeader += "[[[${it}]]]";
            }
            response.getHeaders().set("X-Zuul-Debug-Header", debugHeader)
        }

        if (Debug.debugRequest(response.getContext())) {
            response.getHeaders().entries()?.each { Map.Entry<String, String> it ->
                Debug.addRequestDebug(response.getContext(), "OUTBOUND: <  " + it.key + ":" + it.value)
            }
        }

        return response
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {
        @Mock
        HttpRequestMessage request

        SessionContext context
        HttpResponseMessage response

        @Before
        public void setup() {
            context = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(context)
            response = new HttpResponseMessage(context, request, 99)
        }

        @Test
        public void test() {

            DebugResponse filter = new DebugResponse()
            filter.INCLUDE_DEBUG_HEADER = Mockito.mock(DynamicBooleanProperty.class)
            Mockito.when(filter.INCLUDE_DEBUG_HEADER.get()).thenReturn(true)

            filter.apply(response)

            assertEquals("", response.getHeaders().getFirst("X-Zuul-Debug-Header"))
        }
    }
}
