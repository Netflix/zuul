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
package com.netflix.zuul.filters

import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.constants.ZuulHeaders
import com.netflix.zuul.context.HttpQueryParams
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.SessionContext

/**
 * This is an abstract filter that will route requests that match the patternMatches() method to a debug Eureka "VIP" or
 * host specified by zuul.debug.vip or zuul.debug.host.
 * @author Mikey Cohen
 * Date: 6/27/12
 * Time: 12:54 PM
 */
public abstract class SurgicalDebugFilter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage> {

    /**
     * Returning true by the pattern or logic implemented in this method will route the request to the specified origin
     * @return true if this request should be routed to the debug origin
     */
    abstract def boolean patternMatches()



    @Override
    String filterType() {
        return "pre"
    }

    @Override
    int filterOrder() {
        return 99
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {

        DynamicBooleanProperty debugFilterShutoff = DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_DEBUGFILTERS_DISABLED, false);

        if (debugFilterShutoff.get()) return false;

        if (isDisabled()) return false;

        String isSurgicalFilterRequest = request.getHeaders().getFirst(ZuulHeaders.X_ZUUL_SURGICAL_FILTER)
        if ("true".equals(isSurgicalFilterRequest)) return false; // dont' apply filter if it was already applied

        return patternMatches();
    }


    @Override
    HttpRequestMessage apply(HttpRequestMessage request)
    {
        DynamicStringProperty routeVip = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_DEBUG_VIP, null);
        DynamicStringProperty routeHost = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_DEBUG_HOST, null);

        SessionContext ctx = request.getContext()

        if (routeVip.get() != null || routeHost.get() != null) {

            ctx.getAttributes().routeHost = routeHost.get();
            ctx.getAttributes().routeVIP = routeVip.get();

            request.getHeaders().set(ZuulHeaders.X_ZUUL_SURGICAL_FILTER, "true")

            HttpQueryParams queryParams = request.getQueryParams()
            queryParams.set("debugRequest", "true")

            ctx.getAttributes().setDebugRequest(true)
            ctx.getAttributes().zuulToZuul = true

        }
        return request
    }
}
