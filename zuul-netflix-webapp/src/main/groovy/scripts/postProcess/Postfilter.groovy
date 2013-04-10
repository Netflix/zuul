package scripts.postProcess


import com.netflix.util.Pair
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.stats.ErrorStatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner


import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.netflix.zuul.groovy.ProxyFilter
import com.netflix.zuul.context.RequestContext

class Postfilter extends ProxyFilter {

    Postfilter() {

    }

    boolean shouldFilter() {
        if (true.equals(NFRequestContext.getCurrentContext().proxyToProxy)) return false; //request was proxied to a proxy, so don't send response headers
        return true
    }

    Object run() {
        addStandardResponseHeaders(RequestContext.getCurrentContext().getRequest(), RequestContext.getCurrentContext().getResponse())
        return null;
    }


    void addStandardResponseHeaders(HttpServletRequest req, HttpServletResponse res) {
        println(originatingURL)

        String origin = req.getHeader("Origin")
        RequestContext context = RequestContext.getCurrentContext()
        List<Pair<String, String>> headers = context.getProxyResponseHeaders()
        headers.add(new Pair("X-Netflix-API-Proxy", "zuul"))
        headers.add(new Pair("X-Netflix-API-Proxy-instance", System.getenv("EC2_INSTANCE_ID") ?: "unknown"))
        headers.add(new Pair("Connection", "keep-alive"))
        headers.add(new Pair("X-Originating-URL", originatingURL))

        // trying to force flushes down to clients without Apache mod_proxy buffering
//        headers.add(new Pair("Transfer-Encoding", "chunked"));
//        res.setContentLength(-1);

        if (context.get("ErrorHandled") == null && context.responseStatusCode >= 400) {
            headers.add(new Pair("X-Netflix-Error-Cause", "Error from API Backend"))
            ErrorStatsManager.manager.putStats(RequestContext.getCurrentContext().route, "Error_from_API_Server")

        }
    }

    String getOriginatingURL() {
        HttpServletRequest request = NFRequestContext.getCurrentContext().getRequest();

        String protocol = request.getHeader("X-Forwarded-Proto")
        if (protocol == null) protocol = "http"
        String host = request.getHeader("Host")
        String uri = request.getRequestURI();
        def URL = "${protocol}://${host}${uri}"
        if (request.getQueryString() != null) {
            URL += "?${request.getQueryString()}"
        }
        return URL
    }

    @Override
    String filterType() {
        return 'post'
    }

    @Override
    int filterOrder() {
        return 10
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Before
        public void before() {
            RequestContext.setContextClass(NFRequestContext.class);
        }

        @Test
        public void testHeaderResponse() {

            def f = new Postfilter();
            f = Mockito.spy(f)
            RequestContext.getCurrentContext().setRequest(request)
            RequestContext.getCurrentContext().setResponse(response)
            f.runFilter()
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("X-Netflix-API-Proxy", "api.next.proxy"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("X-Netflix-API-Proxy-instance", System.getenv("EC2_INSTANCE_ID") ?: "unknown"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Origin", "*"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Credentials", "true"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,X-Netflix.application.name,X-Netflix.application.version,X-Netflix.esn,X-Netflix.device.type,X-Netflix.certification.version,X-Netflix.request.uuid,X-Netflix.user.id,X-Netflix.oauth.consumer.key,X-Netflix.oauth.token"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Methods", "GET, POST"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Connection", "keep-alive"))


            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("X-Netflix-API-Proxy", "api.next.proxy")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Origin", "*")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Credentials", "true")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,X-Netflix.application.name,X-Netflix.application.version,X-Netflix.esn,X-Netflix.device.type,X-Netflix.certification.version,X-Netflix.request.uuid,X-Netflix.user.id,X-Netflix.oauth.consumer.key,X-Netflix.oauth.token")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Methods", "GET, POST")))

            Assert.assertTrue(f.filterType().equals("post"))
            Assert.assertTrue(f.shouldFilter())
        }

    }

}