/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package filters.post

import com.netflix.util.Pair
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.context.RequestContext
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

import static com.netflix.zuul.constants.ZuulHeaders.*

class Postfilter extends ZuulFilter {

    Postfilter() {

    }

    boolean shouldFilter() {
        if (true.equals(NFRequestContext.getCurrentContext().zuulToZuul)) return false; //request was routed to a zuul server, so don't send response headers
        return true
    }

    Object run() {
        addStandardResponseHeaders(RequestContext.getCurrentContext().getRequest(), RequestContext.getCurrentContext().getResponse())
        return null;
    }


    void addStandardResponseHeaders(HttpServletRequest req, HttpServletResponse res) {
        println(originatingURL)

        String origin = req.getHeader(ORIGIN)
        RequestContext context = RequestContext.getCurrentContext()
        List<Pair<String, String>> headers = context.getZuulResponseHeaders()
        headers.add(new Pair(X_ZUUL, "zuul"))
        headers.add(new Pair(X_ZUUL_INSTANCE, System.getenv("EC2_INSTANCE_ID") ?: "unknown"))
        headers.add(new Pair(CONNECTION, KEEP_ALIVE))
        headers.add(new Pair(X_ZUUL_FILTER_EXECUTION_STATUS, context.getFilterExecutionSummary().toString()))
        headers.add(new Pair(X_ORIGINATING_URL, originatingURL))

        if (context.get("ErrorHandled") == null && context.responseStatusCode >= 400) {
            headers.add(new Pair(X_NETFLIX_ERROR_CAUSE, "Error from Origin"))
            ErrorStatsManager.manager.putStats(RequestContext.getCurrentContext().route, "Error_from_Origin_Server")

        }
    }

    String getOriginatingURL() {
        HttpServletRequest request = NFRequestContext.getCurrentContext().getRequest();

        String protocol = request.getHeader(X_FORWARDED_PROTO)
        if (protocol == null) protocol = "http"
        String host = request.getHeader(HOST)
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
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("X-Zuul", "Zuul"))
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("X-Zuul-instance", System.getenv("EC2_INSTANCE_ID") ?: "unknown"))
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("Access-Control-Allow-Origin", "*"))
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("Access-Control-Allow-Credentials", "true"))
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,X-Netflix.application.name,X-Netflix.application.version,X-Netflix.esn,X-Netflix.device.type,X-Netflix.certification.version,X-Netflix.request.uuid,X-Netflix.user.id,X-Netflix.oauth.consumer.key,X-Netflix.oauth.token"))
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("Access-Control-Allow-Methods", "GET, POST"))
            RequestContext.getCurrentContext().zuulResponseHeaders.add(new Pair("Connection", "keep-alive"))


            Assert.assertTrue(RequestContext.getCurrentContext().getZuulResponseHeaders().contains(new Pair("X-Zuul", "Zuul")))
            Assert.assertTrue(RequestContext.getCurrentContext().getZuulResponseHeaders().contains(new Pair("Access-Control-Allow-Origin", "*")))
            Assert.assertTrue(RequestContext.getCurrentContext().getZuulResponseHeaders().contains(new Pair("Access-Control-Allow-Credentials", "true")))
            Assert.assertTrue(RequestContext.getCurrentContext().getZuulResponseHeaders().contains(new Pair("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,X-Netflix.application.name,X-Netflix.application.version,X-Netflix.esn,X-Netflix.device.type,X-Netflix.certification.version,X-Netflix.request.uuid,X-Netflix.user.id,X-Netflix.oauth.consumer.key,X-Netflix.oauth.token")))
            Assert.assertTrue(RequestContext.getCurrentContext().getZuulResponseHeaders().contains(new Pair("Access-Control-Allow-Methods", "GET, POST")))

            Assert.assertTrue(f.filterType().equals("post"))
            Assert.assertTrue(f.shouldFilter())
        }

    }

}