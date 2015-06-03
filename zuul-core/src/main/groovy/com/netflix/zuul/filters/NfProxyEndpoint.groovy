package com.netflix.zuul.filters

import com.netflix.zuul.context.*
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.monitoring.MonitoringHelper
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
import rx.Observable

import static org.junit.Assert.*
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
class NfProxyEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    private static final Logger LOG = LoggerFactory.getLogger(NfProxyEndpoint.class);

    @Override
    Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        debug(request.getContext(), request)

        // Get the Origin.
        Origin origin = getOrigin(request)

        // Add execution of the request to the Observable chain, and return.
        Observable<HttpResponseMessage> respObs = origin.request(request)

        // Store the status from origin (in case it's later overwritten).
        respObs = respObs.doOnNext({ originResp ->
            request.getContext().getAttributes().put("origin_http_status", Integer.toString(originResp.getStatus()));
        })

        return respObs
    }

    HttpResponseMessage apply(HttpRequestMessage request)
    {
        debug(request.getContext(), request)

        // Get the Origin.
        Origin origin = getOrigin(request)

        // Add execution of the request to the Observable chain, and block waiting for it to finish.
        HttpResponseMessage response = origin.request(request).toBlocking().first()

        // Store the status from origin (in case it's later overwritten).
        request.getContext().getAttributes().put("origin_http_status", Integer.toString(response.getStatus()));

        return response
    }

    protected Origin getOrigin(HttpRequestMessage request)
    {
        String name = request.getContext().getAttributes().getRouteVIP()
        OriginManager originManager = request.getContext().getHelpers().get("origin_manager")
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!", 500, "UNKNOWN_VIP")
        }
        return origin
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
            response = new HttpResponseMessage(ctx, request, 202)

            when(originManager.getOrigin("an-origin")).thenReturn(origin)
            ctx.getHelpers().put("origin_manager", originManager)
        }

        @Test
        public void testDebug()
        {
            ctx.getAttributes().setDebugRequest(true)

            Headers headers = new Headers()
            headers.add("lah", "deda")

            HttpQueryParams params = new HttpQueryParams()
            params.add("k1", "v1")

            HttpRequestMessage request = new HttpRequestMessage(ctx, "HTTP/1.1", "POST", "/some/where", params, headers, "9.9.9.9", "https")

            filter.debug(ctx, request)

            List<String> debugLines = Debug.getRequestDebug(ctx)
            assertEquals(2, debugLines.size())
            assertEquals("ZUUL:: > lah  deda", debugLines.get(0))
            assertEquals("ZUUL:: > POST  /some/where?k1=v1& HTTP/1.1", debugLines.get(1))
        }

        @Test
        public void testApplyAsync()
        {
            ctx.getAttributes().setRouteVIP("an-origin")
            when(origin.request(request)).thenReturn(Observable.just(response))

            Observable<HttpResponseMessage> respObs = filter.applyAsync(request)
            respObs.toBlocking().single()

            assertEquals("202", ctx.getAttributes().get("origin_http_status"))
        }


        @Test
        public void testApply()
        {
            ctx.getAttributes().setRouteVIP("an-origin")
            when(origin.request(request)).thenReturn(Observable.just(response))

            HttpResponseMessage response = filter.apply(request)

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
                Assert.assertTrue(true)
            }
        }
    }

}