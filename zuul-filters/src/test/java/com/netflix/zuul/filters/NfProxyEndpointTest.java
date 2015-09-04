package com.netflix.zuul.filters;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
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

@RunWith(MockitoJUnitRunner.class)
public class NfProxyEndpointTest {
    @Before
    public void setup() {
        MonitoringHelper.initMocks();
        filter = new NfProxyEndpoint();
        ctx = new SessionContext();
        Mockito.when(request.getContext()).thenReturn(ctx);
        response = new HttpResponseMessageImpl(ctx, request, 202);

        Mockito.when(originManager.getOrigin("an-origin")).thenReturn(origin);
        ctx.put("origin_manager", originManager);
    }

    @Test
    public void testApplyAsync() {
        ctx.setRouteVIP("an-origin");
        Mockito.when(origin.request(request)).thenReturn(Observable.just(response));

        filter.applyAsync(request).toBlocking().single();

        Assert.assertEquals("202", ctx.get("origin_http_status"));
    }

    @Test
    public void testApply() {
        ctx.setRouteVIP("an-origin");
        Mockito.when(origin.request(request)).thenReturn(Observable.just(response));

        filter.apply(request);

        Assert.assertEquals("202", ctx.get("origin_http_status"));
    }

    @Test
    public void testApply_NoOrigin() {
        ctx.setRouteVIP("a-different-origin");
        try {
            Observable<HttpResponseMessage> respObs = filter.applyAsync(request);
            respObs.toBlocking().single();
            Assert.fail();
        } catch (ZuulException e) {
            Assert.assertTrue(true);
        }

    }

    public OriginManager getOriginManager() {
        return originManager;
    }

    public void setOriginManager(OriginManager originManager) {
        this.originManager = originManager;
    }

    public Origin getOrigin() {
        return origin;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    public HttpRequestMessage getRequest() {
        return request;
    }

    public void setRequest(HttpRequestMessage request) {
        this.request = request;
    }

    public NfProxyEndpoint getFilter() {
        return filter;
    }

    public void setFilter(NfProxyEndpoint filter) {
        this.filter = filter;
    }

    public SessionContext getCtx() {
        return ctx;
    }

    public void setCtx(SessionContext ctx) {
        this.ctx = ctx;
    }

    public HttpResponseMessage getResponse() {
        return response;
    }

    public void setResponse(HttpResponseMessage response) {
        this.response = response;
    }

    @Mock
    private OriginManager originManager;
    @Mock
    private Origin origin;
    @Mock
    private HttpRequestMessage request;
    private NfProxyEndpoint filter;
    private SessionContext ctx;
    private HttpResponseMessage response;
}
