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
package inbound

import com.netflix.zuul.context.Debug
import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.http.HttpInboundFilter
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestMessage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner
import rx.Observable

import static org.mockito.Mockito.when

/**
 * @author Mikey Cohen
 * Date: 3/12/12
 * Time: 1:51 PM
 */
class DebugRequest extends HttpInboundFilter
{
    @Override
    int filterOrder() {
        return 10000
    }

    @Override
    boolean shouldFilter(HttpRequestMessage msg) {
        return Debug.debugRequest(msg.getContext())

    }

    @Override
    Observable<HttpRequestMessage> applyAsync(HttpRequestMessage req)
    {
        return Debug.writeDebugRequest(req.getContext(), req.getInboundRequest(), true)
                .map({v -> req})
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {

        @Before
        public void setup() {
            super.setup()
        }

        @Test
        public void testDebug() {

            DebugRequest debugFilter = new DebugRequest()

            when(originalRequest.getClientIp()).thenReturn("1.1.1.1")
            when(originalRequest.getMethod()).thenReturn("method")
            when(originalRequest.getProtocol()).thenReturn("protocol")
            when(originalRequest.getPathAndQuery()).thenReturn("uri")

            Headers headers = new Headers()
            when(originalRequest.getHeaders()).thenReturn(headers)
            headers.add("Host", "moldfarm.com")
            headers.add("X-Forwarded-Proto", "https")

            context.setDebugRequest(true)
            debugFilter.applyAsync(request).toBlocking().first()

            ArrayList<String> debugList = Debug.getRequestDebug(context)

            Assert.assertEquals("REQUEST_INBOUND:: > LINE: METHOD uri protocol", debugList.get(0))
            Assert.assertEquals("REQUEST_INBOUND:: > HDR: X-Forwarded-Proto:https", debugList.get(1))
            Assert.assertEquals("REQUEST_INBOUND:: > HDR: Host:moldfarm.com", debugList.get(2))
        }
    }
}
