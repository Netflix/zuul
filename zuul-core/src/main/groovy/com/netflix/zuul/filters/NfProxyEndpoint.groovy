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
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.*
import com.netflix.zuul.monitoring.MonitoringHelper
import com.netflix.zuul.origins.Origin
import com.netflix.zuul.origins.OriginManager
import com.netflix.zuul.util.HttpUtils
import org.apache.commons.io.IOUtils
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

import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

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
class NfProxyEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessageImpl>
{
    private static final Logger LOG = LoggerFactory.getLogger(NfProxyEndpoint.class);

    @Override
    Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()

        return debugRequest(context, request)
            .map({req ->
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
                return debugResponse(context, originResp)
            })
    }

    HttpResponseMessage apply(HttpRequestMessage request)
    {
        return applyAsync(request).toBlocking().first()
    }

    protected Origin getOrigin(HttpRequestMessage request)
    {
        String name = request.getContext().getRouteVIP()
        OriginManager originManager = request.getContext().get("origin_manager")
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!", "UNKNOWN_VIP")
        }
        return origin
    }

    Observable<HttpRequestMessage> debugRequest(SessionContext context, HttpRequestMessage request)
    {
        Observable<HttpRequestMessage> obs = null

        if (Debug.debugRequest(context)) {

            request.getHeaders().entries().each {
                Debug.addRequestDebug(context, "ZUUL:: > ${it.key}  ${it.value}")
            }
            String query = ""
            request.getQueryParams().entries().each {
                query += it.key + "=" + it.value + "&"
            }

            Debug.addRequestDebug(context, "ZUUL:: > ${request.getMethod()}  ${request.getPath()}?${query} ${request.getProtocol()}")

            if (request.isBodyBuffered()) {
                if (! Debug.debugRequestHeadersOnly()) {
                    obs = request.bufferBody().map({bodyBytes ->
                        String body = bodyToText(bodyBytes, request.getHeaders())
                        Debug.addRequestDebug(context, "ZUUL:: > ${body}")
                    })
                }
            }
        }

        if (obs == null)
            obs = Observable.just(request)

        return obs
    }

    Observable<HttpResponseMessage> debugResponse(SessionContext context, HttpResponseMessage response)
    {
        Observable<HttpResponseMessage> obs = null

        if (Debug.debugRequest(context)) {
            Debug.addRequestDebug(context, "ORIGIN_RESPONSE:: <  STATUS: " + response.getStatus());


            for (Map.Entry header : response.getHeaders().entries()) {
                Debug.addRequestDebug(context, String.format("ORIGIN_RESPONSE:: < %s  %s", header.getKey(), header.getValue()));
            }

            // Capture the response body into a Byte array for later usage.
            if (response.hasBody()) {
                if (! Debug.debugRequestHeadersOnly(context)) {
                    // Convert body to a String and add to debug log.
                    obs = response.bufferBody().map({bodyBytes ->

                        String body = NfProxyEndpoint.bodyToText(bodyBytes, response.getHeaders())
                        Debug.addRequestDebug(context, String.format("ORIGIN_RESPONSE:: < %s", body))

                        return response
                    })
                }
            }
        }

        if (obs == null)
            obs = Observable.just(response)

        return obs
    }

    private static String bodyToText(byte[] bodyBytes, Headers headers)
    {
        if (HttpUtils.isGzipped(headers)) {
            GZIPInputStream gzIn = new GZIPInputStream(new ByteArrayInputStream(bodyBytes));
            bodyBytes = IOUtils.toByteArray(gzIn)
        }
        return IOUtils.toString(bodyBytes, "UTF-8")
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
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessageImpl(ctx, request, 202)

            when(originManager.getOrigin("an-origin")).thenReturn(origin)
            ctx.put("origin_manager", originManager)
        }

        @Test
        public void testDebug()
        {
            ctx.setDebugRequest(true)

            Headers headers = new Headers()
            headers.add("lah", "deda")

            HttpQueryParams params = new HttpQueryParams()
            params.add("k1", "v1")

            HttpRequestMessage request = new HttpRequestMessageImpl(ctx, "HTTP/1.1", "POST", "/some/where",
                    params, headers, "9.9.9.9", "https", 80, "localhost")

            filter.debug(ctx, request)

            List<String> debugLines = Debug.getRequestDebug(ctx)
            assertEquals(2, debugLines.size())
            assertEquals("ZUUL:: > lah  deda", debugLines.get(0))
            assertEquals("ZUUL:: > POST  /some/where?k1=v1& HTTP/1.1", debugLines.get(1))
        }

        @Test
        public void testApplyAsync()
        {
            ctx.setRouteVIP("an-origin")
            when(origin.request(request)).thenReturn(Observable.just(response))

            Observable<HttpRequestMessage> respObs = filter.applyAsync(request)
            respObs.toBlocking().single()

            assertEquals("202", ctx.get("origin_http_status"))
        }


        @Test
        public void testApply()
        {
            ctx.setRouteVIP("an-origin")
            when(origin.request(request)).thenReturn(Observable.just(response))

            HttpResponseMessageImpl response = filter.apply(request)

            assertEquals("202", ctx.get("origin_http_status"))
        }

        @Test
        public void testApply_NoOrigin()
        {
            ctx.setRouteVIP("a-different-origin")
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