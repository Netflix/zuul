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
package filters.inbound

import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.ZuulApplicationInfo
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Attributes
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.BaseSyncFilter

/**
 * @author Mikey Cohen
 * Date: 1/23/13
 * Time: 2:03 PM
 */
class Routing extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage>
{
    DynamicStringProperty defaultClient = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_NIWS_DEFAULTCLIENT, ZuulApplicationInfo.applicationName);
    DynamicStringProperty defaultHost = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_DEFAULT_HOST, null);


    @Override
    int filterOrder() {
        return 1
    }

    @Override
    String filterType() {
        return "in"
    }

    @Override
    boolean shouldFilter(HttpRequestMessage msg) {
        return true
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {

        Attributes attrs = request.getContext().getAttributes()

        // Normalise the uri.
        String uri = request.getPath()
        if (uri == null) uri = "/"

        // Handle OPTIONS requests specially.
        if (request.getMethod().equalsIgnoreCase("options")) {
            attrs.set("endpoint", "Options")
            return request
        }

        // Route healthchecks to the healthcheck endpoint.
        if ("/healthcheck" == request.getPath()) {
            attrs.set("endpoint", "Healthcheck")
            return request
        }

        // Choose VIP or Host, and the endpoint to use for proxying.
        String host = defaultHost.get()
        if (host == null) {
            attrs.set("endpoint", "ZuulNFRequest")
            if (uri.startsWith("/simulator/")) {
                attrs.routeVIP = "simulator"
            } else {
                attrs.routeVIP = defaultClient.get()
            }
        }
        else {
            final URL targetUrl = new URL(host)
            attrs.setRouteHost(targetUrl);
            attrs.routeVIP = null
            attrs.set("endpoint", "ZuulHostRequest")
        }

        if (host == null && attrs.routeVIP == null) {
            throw new ZuulException("default VIP or host not defined. Define: zuul.niws.defaultClient or zuul.default.host", 501, "zuul.niws.defaultClient or zuul.default.host not defined")
        }

        return request
    }
}