package scripts.preProcess



import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.netflix.zuul.groovy.ProxyFilter
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.context.Debug

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 3/12/12
 * Time: 1:51 PM
 * To change this template use File | Settings | File Templates.
 */
class DebugRequest extends ProxyFilter {
    @Override
    String filterType() {
        return 'pre'
    }

    @Override
    int filterOrder() {
        return 10000
    }

    @Override
    boolean shouldFilter() {
       return Debug.debugRequest()
		
    }

    @Override
    Object run() {

        HttpServletRequest req = RequestContext.currentContext.request as HttpServletRequest

        Debug.addRequestDebug("REQUEST:: " + req.getScheme() + " " + req.getRemoteAddr() + ":" + req.getRemotePort())

        Debug.addRequestDebug("REQUEST:: > " + req.getMethod() + " " + req.getRequestURI() + " " + req.getProtocol())

        Iterator headerIt = req.getHeaderNames().iterator()
        while (headerIt.hasNext()) {
            String name = (String) headerIt.next()
            String value = req.getHeader(name)
            Debug.addRequestDebug("REQUEST:: > " + name + ":" + value)

        }

        final RequestContext ctx = RequestContext.getCurrentContext()
        if(!ctx.isChunkedRequestBody()) {
            InputStream inp = ctx.request.getInputStream()
            String body = null
            if (inp != null) {
                body = inp.getText()
                Debug.addRequestDebug("REQUEST:: > " + body)

            }
        }
        return null;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Test
        public void testDebug() {

            DebugRequest debugFilter = new DebugRequest()

            HttpServletRequest request = Mockito.mock(HttpServletRequest.class)
            RequestContext.currentContext.request = request
            Mockito.when(request.remoteAddr).thenReturn("1.1.1.1")
            Mockito.when(request.scheme).thenReturn("scheme")
            Mockito.when(request.method).thenReturn("method")
            Mockito.when(request.protocol).thenReturn("protocol")
            Mockito.when(request.requestURI).thenReturn("uri")
            Mockito.when(request.remotePort).thenReturn(10)
            Mockito.when(request.inputStream).thenReturn(null)
            Mockito.when(request.getHeader("Host")).thenReturn("moldfarm.com")
            Mockito.when(request.getHeader("X-Forwarded-Proto")).thenReturn("https")

            debugFilter.run()

            ArrayList<String> debugList = Debug.requestDebug

            Assert.assertTrue(debugList.contains("REQUEST:: scheme 1.1.1.1:10"))
            Assert.assertTrue(debugList.contains("REQUEST:: > method uri protocol"))


        }

    }
}
