package com.netflix.zuul.filters.endpoints;

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.http.HttpAsyncEndpoint;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.monitoring.MonitoringHelper;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;

/**
 * General-purpose Proxy endpoint implementation with both async and sync/blocking methods.
 * <p>
 * You can probably just subclass this in your project, and use as-is.
 * <p>
 * User: michaels@netflix.com
 * Date: 5/22/15
 * Time: 1:42 PM
 */
public class NfProxyEndpoint extends HttpAsyncEndpoint
{
    @Override
    public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        SessionContext context = request.getContext();

        return Debug.writeDebugRequest(context, request, false)
                .map(bool -> {
                        // Get the Origin.
                        Origin origin = getOrigin(request);
                        return origin;
                })
                .flatMap(origin -> {
                    // Add execution of the request to the Observable chain, and return.
                    Observable<HttpResponseMessage> respObs = origin.request(request);
                    return respObs;
                })
                .doOnNext(originResp -> {
                    // Store the status from origin (in case it's later overwritten).
                    context.put("origin_http_status", Integer.toString(originResp.getStatus()));
                })
                .flatMap(originResp -> {
                    return Debug.writeDebugResponse(context, originResp, true).map(bool -> originResp);
                });
    }

    public HttpResponseMessage apply(HttpRequestMessage request)
    {
        return applyAsync(request).toBlocking().first();
    }

    protected Origin getOrigin(HttpRequestMessage request)
    {
        final String name = request.getContext().getRouteVIP();
        OriginManager originManager = (OriginManager) request.getContext().get("origin_manager");
        Origin origin = originManager.getOrigin(name);
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=" + name + "!", "UNKNOWN_VIP");
        }

        return origin;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        private OriginManager originManager;
        @Mock
        private Origin origin;
        @Mock
        private HttpRequestMessage request;
        private NfProxyEndpoint filter;
        private SessionContext ctx;
        private HttpResponseMessage response;

        @Before
        public void setup()
        {
            MonitoringHelper.initMocks();
            filter = new NfProxyEndpoint();
            ctx = new SessionContext();
            Mockito.when(request.getContext()).thenReturn(ctx);
            response = new HttpResponseMessageImpl(ctx, request, 202);

            Mockito.when(originManager.getOrigin("an-origin")).thenReturn(origin);
            ctx.put("origin_manager", originManager);
        }

        @Test
        public void testApplyAsync()
        {
            ctx.setRouteVIP("an-origin");
            Mockito.when(origin.request(request)).thenReturn(Observable.just(response));

            Observable<HttpResponseMessage> respObs = filter.applyAsync(request);
            respObs.toBlocking().single();

            Assert.assertEquals("202", ctx.get("origin_http_status"));
        }

        @Test
        public void testApply()
        {
            ctx.setRouteVIP("an-origin");
            Mockito.when(origin.request(request)).thenReturn(Observable.just(response));

            filter.apply(request);

            Assert.assertEquals("202", ctx.get("origin_http_status"));
        }

        @Test
        public void testApply_NoOrigin()
        {
            ctx.setRouteVIP("a-different-origin");
            try {
                Observable<HttpResponseMessage> respObs = filter.applyAsync(request);
                respObs.toBlocking().single();
                Assert.fail();
            }
            catch (Exception ZuulException) {
                Assert.assertTrue(true);
            }

        }
    }
}
