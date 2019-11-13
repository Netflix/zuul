/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.zuul.filters.common;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.constants.ZuulHeaders;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;

/**
 * This is an abstract filter that will route requests that match the patternMatches() method to a debug Eureka "VIP" or
 * host specified by zuul.debug.vip or zuul.debug.host.
 *
 * @author Mikey Cohen
 * Date: 6/27/12
 * Time: 12:54 PM
 */
public class SurgicalDebugFilter extends HttpInboundSyncFilter {

    /**
     * Returning true by the pattern or logic implemented in this method will route the request to the specified origin
     *
     * Override this method when using this filter to add your own pattern matching logic.
     *
     * @return true if this request should be routed to the debug origin
     */
    protected boolean patternMatches(HttpRequestMessage request) {
        return false;
    }

    @Override
    public int filterOrder() {
        return 99;
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage request) {

        DynamicBooleanProperty debugFilterShutoff = new DynamicBooleanProperty(ZuulConstants.ZUUL_DEBUGFILTERS_DISABLED, false);

        if (debugFilterShutoff.get()) return false;

        if (isDisabled()) return false;

        String isSurgicalFilterRequest = request.getHeaders().getFirst(ZuulHeaders.X_ZUUL_SURGICAL_FILTER);
        // dont' apply filter if it was already applied
        boolean notAlreadyFiltered = !("true".equals(isSurgicalFilterRequest));

        return notAlreadyFiltered && patternMatches(request);
    }


    @Override
    public HttpRequestMessage apply(HttpRequestMessage request) {
        DynamicStringProperty routeVip = new DynamicStringProperty(ZuulConstants.ZUUL_DEBUG_VIP, null);
        DynamicStringProperty routeHost = new DynamicStringProperty(ZuulConstants.ZUUL_DEBUG_HOST, null);

        SessionContext ctx = request.getContext();

        if (routeVip.get() != null || routeHost.get() != null) {

            ctx.set("routeHost", routeHost.get());
            ctx.set("routeVIP", routeVip.get());

            request.getHeaders().set(ZuulHeaders.X_ZUUL_SURGICAL_FILTER, "true");

            HttpQueryParams queryParams = request.getQueryParams();
            queryParams.set("debugRequest", "true");

            ctx.setDebugRequest(true);
            ctx.set("zuulToZuul", true);

        }
        return request;
    }
}
