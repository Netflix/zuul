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
package filters.endpoint

import com.netflix.client.http.HttpResponse
import com.netflix.zuul.context.*
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.Endpoint
import com.netflix.zuul.monitoring.MonitoringHelper
import com.netflix.zuul.rxnetty.RxNettyOrigin
import com.netflix.zuul.rxnetty.RxNettyOriginManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class ZuulNFRequest extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    private static final Logger LOG = LoggerFactory.getLogger(ZuulNFRequest.class);

    @Override
    Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()
        Attributes attrs = context.getAttributes()

        debug(context, request)

        // Get the Origin.
        String name = attrs.getRouteVIP()
        RxNettyOriginManager originManager = context.getHelpers().get("origin_manager")
        RxNettyOrigin origin = originManager.getOrigin(name)
        if (origin == null) {
            attrs.setShouldSendErrorResponse(true)
            throw new ZuulException("No Origin registered for name=${name}!", 500, "UNKNOWN_VIP")
        }

        // Add execution of the request to the Observable chain, and return.
        Observable<HttpResponseMessage> respObs = origin.request(request)

        // Store the status from origin (in case it's later overwritten).
        respObs = respObs.doOnNext({ originResp ->
            context.getAttributes().put("origin_http_status", Integer.toString(originResp.getStatus()));
        })

        return respObs
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
    public static class TestUnit
    {
        @Mock
        RxNettyOriginManager originManager
        @Mock
        RxNettyOrigin origin

        @Mock
        HttpRequestMessage request

        ZuulNFRequest filter
        SessionContext ctx
        HttpResponseMessage response

        @Before
        public void setup()
        {
            MonitoringHelper.initMocks();
            filter = new ZuulNFRequest()
            ctx = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessage(ctx, request, 202)

            when(originManager.getOrigin("an-origin")).thenReturn(origin)
            ctx.getHelpers().put("origin_manager", originManager)
        }

        @Test
        public void testApply()
        {
            ctx.getAttributes().setRouteVIP("an-origin")
            when(origin.request(request)).thenReturn(Observable.just(response))

            Observable<HttpResponseMessage> respObs = filter.applyAsync(request)
            respObs.toBlocking().single()

            assertEquals("202", ctx.getAttributes().get("origin_http_status"))
        }

        @Test
        public void testApply_NoOrigin()
        {
            ctx.getAttributes().setRouteVIP("a-different-origin")
            try {
                Observable<HttpResponseMessage> respObs = filter.applyAsync(request)
                respObs.toBlocking().single()
                fail()
            }
            catch(ZuulException) {
                //
            }
            assertTrue(ctx.getAttributes().shouldSendErrorResponse())
        }
    }

}



