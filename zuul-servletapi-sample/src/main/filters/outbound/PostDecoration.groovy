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
package outbound

import com.netflix.zuul.context.*
import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.FilterType
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestInfo
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import com.netflix.zuul.stats.ErrorStatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static com.netflix.zuul.constants.ZuulHeaders.*
import static org.mockito.Mockito.when

class PostDecoration extends HttpOutboundSyncFilter
{
    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        if (true.equals(response.getContext().zuulToZuul)) return false; //request was routed to a zuul server, so don't send response headers
        return true
    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response) {
        addStandardResponseHeaders(response)
        return response;
    }


    void addStandardResponseHeaders(HttpResponseMessage response) {

        String originatingURL = getOriginatingURL(response.getInboundRequest())
        SessionContext context = response.getContext()

        Headers headers = response.getHeaders()
        headers.add(X_ZUUL, "zuul")
        headers.add(X_ZUUL_INSTANCE, System.getenv("EC2_INSTANCE_ID") ?: "unknown")
        headers.add(CONNECTION, KEEP_ALIVE)
        headers.add(X_ZUUL_FILTER_EXECUTION_STATUS, context.getFilterExecutionSummary().toString())
        headers.add(X_ORIGINATING_URL, originatingURL)

        if (context.get("ErrorHandled") == null && response.getStatus() >= 400) {
            headers.add(X_NETFLIX_ERROR_CAUSE, "Error from Origin")
            ErrorStatsManager.manager.putStats(context.route, "Error_from_Origin_Server")
        }
    }

    String getOriginatingURL(HttpRequestInfo request)
    {
        String protocol = request.getHeaders().getFirst(X_FORWARDED_PROTO)
        if (protocol == null) protocol = "http"
        String host = request.getHeaders().getFirst(HOST)
        String uri = request.getPathAndQuery();
        def URL = "${protocol}://${host}${uri}"
        return URL
    }

    @Override
    int filterOrder() {
        return 10
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {

        PostDecoration filter

        @Before
        public void setup() {
            super.setup()
            filter = Mockito.spy(new PostDecoration())
        }

        @Test
        public void testHeaderResponse() {

            filter.apply(response)

            Headers respHeaders = response.getHeaders()

            Assert.assertTrue(respHeaders.contains("X-Zuul", "zuul"))
            Assert.assertTrue(respHeaders.contains("X-Zuul-Instance", "unknown"))
            Assert.assertTrue(respHeaders.contains("Connection", "keep-alive"))

            Assert.assertEquals(FilterType.OUTBOUND, filter.filterType())
            Assert.assertTrue(filter.shouldFilter(response))
        }

    }

}