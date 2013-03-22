package com.netflix.zuul.context;

import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.zuul.context.RequestContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 12/23/11
 * Time: 1:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class NFRequestContext extends RequestContext {

    private static final String ARCHIVED_PROPS_KEY = "archivedProperties";
    private static final String EVENT_PROPS_KEY = "eventProperties";

    static {
        RequestContext.setContextClass(NFRequestContext.class);
    }

    public NFRequestContext(){
        super();
        put(ARCHIVED_PROPS_KEY, new HashMap<String, String>());
        put(EVENT_PROPS_KEY, new HashMap<String, Object>());
    }

    public static NFRequestContext getCurrentContext() {
        return (NFRequestContext) RequestContext.threadLocal.get();
    }


    public String getProxyVIP() {
        return (String) get("proxyVIP");
    }

    public void setProxyVIP(String sVip) {
        set("proxyVIP", sVip);
    }

    public boolean hasProxyVIPOrHost() {
        return (getProxyVIP() != null) || (getProxyHost() != null);
    }

    public void unset() {
        if (getProxyResponse() != null) {
            getProxyResponse().releaseResources();
        }
        super.unset();
    }


    public void setRequestEntity(InputStream entity) {
        set("requestEntity", entity);
    }

    public InputStream getRequestEntity() {
        return (InputStream) get("requestEntity");
    }


    public void setProxyResponse(HttpClientResponse response) {
        set("proxyResponse", response);
    }

    public HttpClientResponse getProxyResponse() {
        return (HttpClientResponse) get("proxyResponse");
    }

    public Map<String, String> getOAuthHeaders() {
        return (Map<String, String>) get("oauthHeaders");
    }

    public void setOAuthHeaders(Map<String, String> headers) {
        put("oauthHeaders", headers);
    }

    public String getRoute() {
        return (String) get("route");
    }


    public Boolean areOverridesDisabled() {
        return (Boolean) get("overridesDisabled");
    }

    public void setDisableOverrides(Boolean b) {
        put("overridesDisabled", b);
    }

    public void setArchivedProperty(String key, String value) {
        getArchivedProperties().put(key, value);
    }

    public Map<String, String> getArchivedProperties() {
        return (Map<String, String>) this.get(ARCHIVED_PROPS_KEY);
    }
    

    public void setEventProperty(String key, Object value) {
        getEventProperties().put(key, value);
    }

    public Map<String, Object> getEventProperties() {
        return (Map<String, Object>) this.get(EVENT_PROPS_KEY);
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        private HttpClientResponse clientResponse;

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
            context.setProxyResponse(clientResponse);
            assertEquals(context.getProxyResponse(), clientResponse);
            context.setProxyVIP("vip");
            assertEquals("vip", context.getProxyVIP());
        }
    }

}
