/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.filters

import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.http.HttpAsyncEndpoint
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import com.netflix.zuul.monitoring.MonitoringHelper
import com.netflix.zuul.origins.Origin
import com.netflix.zuul.origins.OriginManager
import com.netflix.zuul.routing.Route
import com.netflix.zuul.routing.RoutingResult
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
import static org.junit.Assert.fail
import static org.mockito.Mockito.when

/**
 * General-purpose Proxy endpoint implementation with both async and sync/blocking methods.
 *
 * You can probably just subclass this in your project, and use as-is.
 *
 * User: michaels@netflix.com
 * Date: 5/22/15
 * Time: 1:42 PM
 */
class NfProxyEndpoint extends HttpAsyncEndpoint
{
    private static final Logger LOG = LoggerFactory.getLogger(NfProxyEndpoint.class);

    @Override
    Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()

        return Debug.writeDebugRequest(context, request, false)
            .map({bool ->
                // Get the Origin.
                Origin origin = getOrigin(request)
                return origin
            })
            .flatMap({origin ->
                // Add execution of the request to the Observable chain, and return.
                Observable<HttpResponseMessage> respObs = origin.request(request)
                return respObs
            })
            .doOnNext({ originResp ->
                // Store the status from origin (in case it's later overwritten).
                context.put("origin_http_status", Integer.toString(originResp.getStatus()));
            })
            .flatMap({originResp ->
                return Debug.writeDebugResponse(context, originResp, true).map({bool -> originResp});
            });
    }

    HttpResponseMessage apply(HttpRequestMessage request)
    {
        return applyAsync(request).toBlocking().first()
    }

    protected Origin getOrigin(HttpRequestMessage request)
    {
        String name = request.getContext().getRoutingResult().getAssigned().getVip()
        OriginManager originManager = request.getContext().get("origin_manager")
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!", "UNKNOWN_VIP")
        }
        return origin
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        OriginManager originManager
        @Mock
        Origin origin

        @Mock
        HttpRequestMessage request

        NfProxyEndpoint filter
        SessionContext ctx
        HttpResponseMessage response

        @Before
        public void setup()
        {
            MonitoringHelper.initMocks();
            filter = new NfProxyEndpoint()
            ctx = new SessionContext()
            ctx.setRoutingResult(new RoutingResult());
            ctx.getRoutingResult().assign(new Route("an-origin"), "test");
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessageImpl(ctx, request, 202)

            when(originManager.getOrigin("an-origin")).thenReturn(origin)
            ctx.put("origin_manager", originManager)
        }

        @Test
        public void testApplyAsync()
        {
            when(origin.request(request)).thenReturn(Observable.just(response))

            Observable<HttpRequestMessage> respObs = filter.applyAsync(request)
            respObs.toBlocking().single()

            assertEquals("202", ctx.get("origin_http_status"))
        }


        @Test
        public void testApply()
        {
            when(origin.request(request)).thenReturn(Observable.just(response))

            HttpResponseMessageImpl response = filter.apply(request)

            assertEquals("202", ctx.get("origin_http_status"))
        }

        @Test
        public void testApply_NoOrigin()
        {
            ctx.getRoutingResult().assign(new Route("a-different-origin"), "test")
            try {
                Observable<HttpResponseMessageImpl> respObs = filter.applyAsync(request)
                respObs.toBlocking().single()
                fail()
            }
            catch(ZuulException) {
                Assert.assertTrue(true)
            }
        }
    }

}