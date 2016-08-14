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
package com.netflix.zuul.context;

import com.netflix.client.http.HttpResponse;
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
 * Extended RequestContext adding Netflix library specific concepts and data
 *
 * @author Mikey Cohen
 *         Date: 12/23/11
 *         Time: 1:14 PM
 */
public class NFRequestContext extends RequestContext {

    private static final String EVENT_PROPS_KEY = "eventProperties";


    static {
        RequestContext.setContextClass(NFRequestContext.class);

    }

    /**
     * creates a new NFRequestContext
     */
    public NFRequestContext() {
        super();
        put(EVENT_PROPS_KEY, new HashMap<String, Object>());

    }

    /**
     * returns a NFRequestContext from the threadLocal
     *
     * @return
     */
    public static NFRequestContext getCurrentContext() {
        return (NFRequestContext) RequestContext.threadLocal.get();
    }

    /**
     * returns the routeVIP; that is the Eureka "vip" of registered instances
     *
     * @return
     */
    public String getRouteVIP() {
        return (String) get("routeVIP");
    }

    /**
     * sets routeVIP; that is the Eureka "vip" of registered instances
     *
     * @return
     */

    public void setRouteVIP(String sVip) {
        set("routeVIP", sVip);
    }

    /**
     * @return true if a routeHost or routeVip has been defined
     */
    public boolean hasRouteVIPOrHost() {
        return (getRouteVIP() != null) || (getRouteHost() != null);
    }

    /**
     * unsets the requestContextVariables
     */
    @Override
    public void unset() {
        if (getZuulResponse() != null) {
            getZuulResponse().close(); //check this?
        }
        super.unset();
    }

    /**
     * sets the requestEntity; the inputStream of the Request
     *
     * @param entity
     */
    public void setRequestEntity(InputStream entity) {
        set("requestEntity", entity);
    }

    /**
     * @return the requestEntity; the inputStream of the request
     */
    public InputStream getRequestEntity() {
        return (InputStream) get("requestEntity");
    }

    /**
     * Sets the HttpResponse response that comes back from a Ribbon client.
     *
     * @param response
     */
    public void setZuulResponse(HttpResponse response) {
        set("zuulResponse", response);
    }

    /**
     * gets the "zuulResponse"
     *
     * @return returns the HttpResponse from a Ribbon call to an origin
     */
    public HttpResponse getZuulResponse() {
        return (HttpResponse) get("zuulResponse");
    }

    /**
     * returns the "route". This is a Zuul defined bucket for collecting request metrics. By default the route is the
     * first segment of the uri  eg /get/my/stuff : route is "get"
     *
     * @return
     */
    public String getRoute() {
        return (String) get("route");
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

}
