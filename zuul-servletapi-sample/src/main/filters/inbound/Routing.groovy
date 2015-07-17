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
package inbound

import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.ZuulApplicationInfo
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.http.HttpInboundSyncFilter

/**
 * @author Mikey Cohen
 * Date: 1/23/13
 * Time: 2:03 PM
 */
class Routing extends HttpInboundSyncFilter
{
    DynamicStringProperty defaultClient = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_NIWS_DEFAULTCLIENT, ZuulApplicationInfo.applicationName);
    DynamicStringProperty defaultHost = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_DEFAULT_HOST, null);

    @Override
    int filterOrder() {
        return 1
    }

    @Override
    boolean shouldFilter(HttpRequestMessage msg) {
        return true
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {

        SessionContext context = request.getContext()

        // Normalise the uri.
        String uri = request.getPath()
        if (uri == null) uri = "/"

        // Handle OPTIONS requests specially.
        if (request.getMethod().equalsIgnoreCase("options")) {
            context.setEndpoint("endpoint.Options")
            return request
        }

        // Route healthchecks to the healthcheck endpoint.
        if ("/healthcheck" == request.getPath()) {
            context.setEndpoint("endpoint.Healthcheck")
            return request
        }

        // Choose VIP or Host, and the endpoint to use for proxying.
        String host = defaultHost.get()
        if (host == null) {
            context.setEndpoint("endpoint.ZuulNFRequest")
            if (uri.startsWith("/simulator/")) {
                context.routeVIP = "simulator"
            } else {
                context.routeVIP = defaultClient.get()
            }
        }
        else {
            final URL targetUrl = new URL(host)
            context.setRouteHost(targetUrl);
            context.routeVIP = null
            context.setEndpoint("endpoint.ZuulHostRequest")
        }

        if (host == null && context.routeVIP == null) {
            throw new ZuulException("default VIP or host not defined. Define: zuul.niws.defaultClient or zuul.default.host", "zuul.niws.defaultClient or zuul.default.host not defined")
        }

        return request
    }
}