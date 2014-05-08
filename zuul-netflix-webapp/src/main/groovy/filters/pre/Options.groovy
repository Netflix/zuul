import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.filters.StaticResponseFilter
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.junit.Assert.assertTrue
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/1/12
 * Time: 7:56 AM
 */
class Options extends StaticResponseFilter {

    boolean shouldFilter() {
        String method = RequestContext.currentContext.getRequest() getMethod();
        if (method.equalsIgnoreCase("options")) return true;
    }


    @Override
    String uri() {
        return "any path here"
    }

    @Override
    String responseBody() {
        return "" // empty response
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Test
        public void testClientAccessPolicy() {

            RequestContext.setContextClass(RequestContext.class);

            Options options = new Options()

            HttpServletRequest request = mock(HttpServletRequest.class)
            RequestContext.currentContext.request = request
            when(request.getRequestURI()).thenReturn("/anything")
            when(request.getMethod()).thenReturn("OPTIONS")
            options.run()

            assertTrue(options.shouldFilter())

            assertTrue(RequestContext.currentContext.responseBody != null)
            assertTrue(RequestContext.currentContext.getResponseBody().isEmpty())
        }

    }

}
