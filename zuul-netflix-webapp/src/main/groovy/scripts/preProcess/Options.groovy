package scripts.preProcess


import com.netflix.zuul.context.NFRequestContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.netflix.zuul.filters.StaticResponseFilter
import com.netflix.zuul.context.RequestContext

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/1/12
 * Time: 7:56 AM
 * To change this template use File | Settings | File Templates.
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

            RequestContext.setContextClass(NFRequestContext.class);

            Options options = new Options()

            HttpServletRequest request = Mockito.mock(HttpServletRequest.class)
            RequestContext.currentContext.request = request
            Mockito.when(request.getRequestURI()).thenReturn("/anything")
            Mockito.when(request.getMethod()).thenReturn("OPTIONS")
            options.run()

            Assert.assertTrue(options.shouldFilter())

            Assert.assertTrue(RequestContext.currentContext.responseBody != null)
            Assert.assertTrue(RequestContext.currentContext.getResponseBody().isEmpty())
        }

    }

}
