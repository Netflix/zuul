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

import com.netflix.zuul.context.*
import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
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
    public static class TestUnit {

        PreDecoration filter
        SessionContext ctx
        HttpResponseMessage response
        Headers reqHeaders

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = new PreDecoration()

            ctx = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessageImpl(ctx, request, 99)
            reqHeaders = new Headers()
            Mockito.when(request.getHeaders()).thenReturn(reqHeaders)
        }

        @Test
        public void testPreHeaders() {

            Mockito.when(request.getClientIp()).thenReturn("1.1.1.1")
            reqHeaders.set("Host", "moldfarm.com")
            reqHeaders.set("X-Forwarded-Proto", "https")

            filter.setOriginRequestHeaders(request)

            Assert.assertNotNull(reqHeaders.getFirst("x-netflix.request.toplevel.uuid"))
            Assert.assertNotNull(reqHeaders.getFirst("x-forwarded-for"))
            Assert.assertNotNull(reqHeaders.getFirst("x-netflix.client-host"))
            Assert.assertNotNull(reqHeaders.getFirst("x-netflix.client-proto"))
        }
    }
}
