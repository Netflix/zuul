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
package filters.inbound

import com.netflix.zuul.context.*
import com.netflix.zuul.filters.BaseSyncFilter
import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

/**
 * @author Mikey Cohen
 * Date: 3/12/12
 * Time: 1:51 PM
 */
class DebugRequest extends HttpInboundSyncFilter
{
    @Override
    int filterOrder() {
        return 10000
    }

    @Override
    boolean shouldFilter(HttpRequestMessage msg) {
        return com.netflix.zuul.context.Debug.debugRequest(msg.getContext())

    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage req) {

        com.netflix.zuul.context.Debug.addRequestDebug(req.getContext(), "REQUEST:: " + req.getProtocol() + " " + req.getClientIp())

        com.netflix.zuul.context.Debug.addRequestDebug(req.getContext(), "REQUEST:: > " + req.getMethod() + " " + req.getPathAndQuery() + " " + req.getProtocol())

        for (Map.Entry header : req.getHeaders().entries()) {
            com.netflix.zuul.context.Debug.addRequestDebug(req.getContext(), "REQUEST:: > " + header.getKey() + ":" + header.getValue())
        }

        if (req.getBody()) {
            String bodyStr = new String(req.getBody(), "UTF-8");
            com.netflix.zuul.context.Debug.addRequestDebug(req.getContext(), "REQUEST:: > " + bodyStr)
        }

        return req;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpResponseMessage response
        @Mock
        HttpRequestMessage request

        SessionContext context

        @Before
        public void setup() {
            context = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(context)
        }

        @Test
        public void testDebug() {

            DebugRequest debugFilter = new DebugRequest()

            Mockito.when(request.getClientIp()).thenReturn("1.1.1.1")
            Mockito.when(request.method).thenReturn("method")
            Mockito.when(request.protocol).thenReturn("protocol")
            Mockito.when(request.getPathAndQuery()).thenReturn("uri")

            Headers headers = new Headers()
            Mockito.when(request.getHeaders()).thenReturn(headers)
            headers.add("Host", "moldfarm.com")
            headers.add("X-Forwarded-Proto", "https")

            debugFilter.apply(request)

            ArrayList<String> debugList = com.netflix.zuul.context.Debug.getRequestDebug(context)

            Assert.assertTrue(debugList.contains("REQUEST:: protocol 1.1.1.1"))
            Assert.assertTrue(debugList.contains("REQUEST:: > method uri protocol"))
            Assert.assertTrue(debugList.contains("REQUEST:: > host:moldfarm.com"))


        }

    }
}
