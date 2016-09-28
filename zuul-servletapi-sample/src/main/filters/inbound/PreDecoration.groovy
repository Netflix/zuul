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

import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import com.netflix.zuul.message.http.HttpRequestMessage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import static com.netflix.zuul.constants.ZuulHeaders.*

/**
 * @author Mikey Cohen
 * Date: 1/5/12
 * Time: 1:03 PM
 */
public class PreDecoration extends HttpInboundSyncFilter
{
    @Override
    int filterOrder() {
        return 20
    }

    @Override
    boolean shouldFilter(HttpRequestMessage req) {
        return true
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {
        setOriginRequestHeaders(request)
        return request
    }

    void setOriginRequestHeaders(HttpRequestMessage request) {

        request.getHeaders().set("X-Netflix.request.toplevel.uuid", UUID.randomUUID().toString())
        request.getHeaders().set(X_FORWARDED_FOR, request.getClientIp())

        String host = request.getHeaders().getFirst(HOST)
        if (host) {
            request.getHeaders().set(X_NETFLIX_CLIENT_HOST, host)
        }

        String xfproto = request.getHeaders().getFirst(X_FORWARDED_PROTO)
        if (xfproto != null) {
            request.getHeaders().set(X_NETFLIX_CLIENT_PROTO, xfproto)
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {

        PreDecoration filter

        @Before
        public void setup() {
            super.setup()
            filter = new PreDecoration()
        }

        @Test
        public void testPreHeaders() {

            Mockito.when(request.getClientIp()).thenReturn("1.1.1.1")
            requestHeaders.set("Host", "moldfarm.com")
            requestHeaders.set("X-Forwarded-Proto", "https")

            filter.setOriginRequestHeaders(request)

            Assert.assertNotNull(requestHeaders.getFirst("x-netflix.request.toplevel.uuid"))
            Assert.assertNotNull(requestHeaders.getFirst("x-forwarded-for"))
            Assert.assertNotNull(requestHeaders.getFirst("x-netflix.client-host"))
            Assert.assertNotNull(requestHeaders.getFirst("x-netflix.client-proto"))
        }
    }
}
