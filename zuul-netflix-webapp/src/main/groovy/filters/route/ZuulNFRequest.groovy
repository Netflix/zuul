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
package route

import com.netflix.client.http.HttpResponse
import com.netflix.zuul.context.*
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.BaseSyncFilter
import com.netflix.zuul.origins.Origin
import com.netflix.zuul.origins.OriginManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ZuulNFRequest extends BaseSyncFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ZuulNFRequest.class);


    @Override
    String filterType() {
        return 'route'
    }

    @Override
    int filterOrder() {
        return 10
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return (ctx.getAttributes().getRouteHost() == null
                && ! ctx.getAttributes().shouldSendErrorResponse()
                && ctx.getAttributes().shouldProxy())
    }

    @Override
    SessionContext apply(SessionContext context)
    {
        HttpRequestMessage request = context.getRequest()
        Attributes attrs = context.getAttributes()

        debug(context, request)

        // Get the Origin.
        String name = attrs.getRouteVIP()
        OriginManager originManager = context.getHelpers().get("origin_manager")
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!", 500, "UNKNOWN_VIP")
        }

        // Add execution of the request to the Observable chain, and block waiting for it to finish.
        context = origin.request(context).toBlocking().first()

        return context
    }



    void debug(SessionContext context, HttpRequestMessage request) {

        if (Debug.debugRequest(context)) {

            request.getHeaders().entries().each {
                Debug.addRequestDebug(context, "ZUUL:: > ${it.key}  ${it.value}")
            }
            String query = ""
            request.getQueryParams().entries().each {
                query += it.key + "=" + it.value + "&"
            }

            Debug.addRequestDebug(context, "ZUUL:: > ${request.getMethod()}  ${request.getPath()}?${query} ${request.getProtocol()}")

            if (request.getBody() != null) {
                if (!Debug.debugRequestHeadersOnly()) {
                    String entity = new ByteArrayInputStream(request.getBody()).getText()
                    Debug.addRequestDebug(context, "ZUUL:: > ${entity}")
                }
            }
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpResponse proxyResp
        @Mock
        HttpResponseMessage response
        @Mock
        HttpRequestMessage request

        SessionContext ctx

        @Before
        public void setup()
        {
            ctx = new SessionContext(request, response)
        }

        @Test
        public void testShouldFilter() {

            Attributes attrs = new Attributes()
            attrs.setRouteHost(new URL("http://www.moldfarm.com"))
            Mockito.when(ctx.getAttributes()).thenReturn(attrs)

            ZuulNFRequest filter = new ZuulNFRequest()
            Assert.assertFalse(filter.shouldFilter())
        }
    }

}



