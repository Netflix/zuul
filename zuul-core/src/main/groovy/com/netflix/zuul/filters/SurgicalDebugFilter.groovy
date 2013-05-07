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

import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.groovy.ZuulFilter
import com.netflix.zuul.util.HTTPRequestUtils
import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty

/**
 * This is an abstract filter that will route requests that match the patternMatches() method to a debug Eureka "VIP" or
 * host specified by zuul.debug.vip or zuul.debug.host.
 * @author Mikey Cohen
 * Date: 6/27/12
 * Time: 12:54 PM
 */
public abstract class SurgicalDebugFilter extends ZuulFilter {

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

    boolean shouldFilter() {

        DynamicBooleanProperty debugFilterShutoff = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.debugFilters.disabed", false);

        if (debugFilterShutoff.get()) return false;

        if (isFilterDisabled()) return false;


        String isSurgicalFilterRequest = RequestContext.currentContext.getRequest().getHeader("X-Zuul-Surgical-Filter");
        if ("true".equals(isSurgicalFilterRequest)) return false; // dont' apply filter if it was already applied
        return patternMatches();
    }


    @Override
    Object run() {
        DynamicStringProperty routeVip = DynamicPropertyFactory.getInstance().getStringProperty("zuul.debug.vip", null);
        DynamicStringProperty routeHost = DynamicPropertyFactory.getInstance().getStringProperty("zuul.debug.host", null);

        if(routeVip.get() != null || routeHost.get() != null){
            RequestContext.currentContext.routeHost = routeHost.get();
            RequestContext.currentContext.routeVIP = routeVip.get();
            RequestContext.currentContext.addZuulRequestHeader("X-Zuul-Surgical-Filter", "true");
            if (HTTPRequestUtils.getInstance().getQueryParams() == null) {
                RequestContext.getCurrentContext().setRequestQueryParams(new HashMap<String, List<String>>());
            }
            HTTPRequestUtils.getInstance().getQueryParams().put("debugRequest", ["true"])
            RequestContext.currentContext.setDebugRequest(true)
            RequestContext.getCurrentContext().proxyToProxy = true

        }
    }
}
