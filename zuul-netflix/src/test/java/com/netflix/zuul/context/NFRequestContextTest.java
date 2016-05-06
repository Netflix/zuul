package com.netflix.zuul.context;

import com.netflix.client.http.HttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class NFRequestContextTest {

    @Mock
    private HttpResponse clientResponse;

    @Before
    public void before() {
        RequestContext.getCurrentContext().unset();
        RequestContext.setContextClass(NFRequestContext.class);
        MockitoAnnotations.initMocks(this);

    }

    @Test
    public void testGetContext() {
        RequestContext.setContextClass(NFRequestContext.class);

        NFRequestContext context = NFRequestContext.getCurrentContext();
        assertNotNull(context);
        Assert.assertEquals(context.getClass(), NFRequestContext.class);

        RequestContext context1 = RequestContext.getCurrentContext();
        assertNotNull(context1);
        Assert.assertEquals(context1.getClass(), NFRequestContext.class);

    }

    @Test
    public void testSetContextVariable() {
        NFRequestContext context = NFRequestContext.getCurrentContext();
        assertNotNull(context);
        context.set("test", "moo");
        Assert.assertEquals(context.get("test"), "moo");
    }

    @Test
    public void testNFRequestContext() {
        NFRequestContext context = NFRequestContext.getCurrentContext();
        context.setZuulResponse(clientResponse);
        assertEquals(context.getZuulResponse(), clientResponse);
        context.setRouteVIP("vip");
        assertEquals("vip", context.getRouteVIP());
    }
}

