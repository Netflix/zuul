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
package post

import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.Headers
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.stats.ErrorStatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static com.netflix.zuul.constants.ZuulHeaders.*

class PostDecoration extends ZuulFilter {

    PostDecoration() {

    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        if (true.equals(ctx.getAttributes().zuulToZuul)) return false; //request was routed to a zuul server, so don't send response headers
        return true
    }

    @Override
    SessionContext apply(SessionContext ctx) {
        addStandardResponseHeaders(ctx)
        return ctx;
    }


    void addStandardResponseHeaders(SessionContext ctx) {

        String originatingURL = getOriginatingURL(ctx.getRequest())
        println(originatingURL)

        HttpResponseMessage resp = ctx.getResponse()

        Headers headers = resp.getHeaders()
        headers.add(X_ZUUL, "zuul")
        headers.add(X_ZUUL_INSTANCE, System.getenv("EC2_INSTANCE_ID") ?: "unknown")
        headers.add(CONNECTION, KEEP_ALIVE)
        headers.add(X_ZUUL_FILTER_EXECUTION_STATUS, ctx.getAttributes().getFilterExecutionSummary().toString())
        headers.add(X_ORIGINATING_URL, originatingURL)

        if (ctx.getAttributes().get("ErrorHandled") == null && resp.getStatus() >= 400) {
            headers.add(X_NETFLIX_ERROR_CAUSE, "Error from Origin")
            ErrorStatsManager.manager.putStats(ctx.getAttributes().route, "Error_from_Origin_Server")
        }
    }

    String getOriginatingURL(HttpRequestMessage request)
    {
        String protocol = request.getHeaders().getFirst(X_FORWARDED_PROTO)
        if (protocol == null) protocol = "http"
        String host = request.getHeaders().getFirst(HOST)
        String uri = request.getPathAndQuery();
        def URL = "${protocol}://${host}${uri}"
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

        PostDecoration filter
        SessionContext ctx
        HttpResponseMessage response
        Headers reqHeaders

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = Mockito.spy(new PostDecoration())
            response = new HttpResponseMessage(99)
            ctx = new SessionContext(request, response)

            reqHeaders = new Headers()
            Mockito.when(request.getHeaders()).thenReturn(reqHeaders)
        }

        @Test
        public void testHeaderResponse() {

            filter.apply(ctx)

            Headers respHeaders = response.getHeaders()

            Assert.assertTrue(respHeaders.contains("X-Zuul", "zuul"))
            Assert.assertTrue(respHeaders.contains("X-Zuul-Instance", "unknown"))
            Assert.assertTrue(respHeaders.contains("Connection", "keep-alive"))

            Assert.assertTrue(filter.filterType().equals("post"))
            Assert.assertTrue(filter.shouldFilter(ctx))
        }

    }

}