package inbound;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.origins.OriginManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PreDecorationTest {

    PreDecoration filter;
    SessionContext ctx;
    HttpResponseMessage response;
    Headers reqHeaders;

    @Mock
    HttpRequestMessage request;
    @Mock
    OriginManager originManager;

    @Before
    public void setup() {
        filter = new PreDecoration();

        ctx = new SessionContext(originManager);
        Mockito.when(request.getContext()).thenReturn(ctx);
        response = new HttpResponseMessageImpl(ctx, request, 99);
        reqHeaders = new Headers();
        Mockito.when(request.getHeaders()).thenReturn(reqHeaders);
    }

    @Test
    public void testPreHeaders() {

        Mockito.when(request.getClientIp()).thenReturn("1.1.1.1");
        reqHeaders.set("Host", "moldfarm.com");
        reqHeaders.set("X-Forwarded-Proto", "https");

        filter.setOriginRequestHeaders(request);

        Assert.assertNotNull(reqHeaders.getFirst("x-netflix.request.toplevel.uuid"));
        Assert.assertNotNull(reqHeaders.getFirst("x-forwarded-for"));
        Assert.assertNotNull(reqHeaders.getFirst("x-netflix.client-host"));
        Assert.assertNotNull(reqHeaders.getFirst("x-netflix.client-proto"));
    }
}