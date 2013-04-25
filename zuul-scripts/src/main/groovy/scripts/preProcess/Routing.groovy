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
package scripts.preProcess



import com.netflix.zuul.ZuulApplicationInfo
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.groovy.ZuulFilter
import com.netflix.zuul.groovy.FilterProcessor
import com.netflix.zuul.context.RequestContext
import com.netflix.config.DynamicStringProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.groovy.ZuulFilter
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.groovy.FilterProcessor

/**
 * @author Mikey Cohen
 * Date: 1/23/13
 * Time: 2:03 PM
 */
class Routing extends ZuulFilter {
    DynamicStringProperty defaultClient = DynamicPropertyFactory.getInstance().getStringProperty("zuul.niws.defaultClient", ZuulApplicationInfo.applicationName);
    DynamicStringProperty defaultHost = DynamicPropertyFactory.getInstance().getStringProperty("zuul.default.host", null);


    @Override
    int filterOrder() {
        return 1
    }

    @Override
    String filterType() {
        return "pre"
    }

    boolean shouldFilter() {
        return true
    }


    Object staticRouting() {
        FilterProcessor.instance.runFilters("healthcheck")
        FilterProcessor.instance.runFilters("static")
    }

    Object run() {

        staticRouting() //runs the static Zuul

        ((NFRequestContext) RequestContext.currentContext).proxyVIP = defaultClient.get()
        String host = defaultHost.get()
        if(((NFRequestContext) RequestContext.currentContext).proxyVIP == null) ((NFRequestContext) RequestContext.currentContext).proxyVIP  = ZuulApplicationInfo.applicationName
        if (host != null) {
            final URL targetUrl = new URL(host)
            RequestContext.currentContext.setProxyHost(targetUrl);
            ((NFRequestContext) RequestContext.currentContext).proxyVIP=null
        }

        if (host == null && RequestContext.currentContext.proxyVIP  == null) {
            throw new ZuulException("default VIP or host not defined. Define: zuul.niws.defaultClient or zuul.default.host", 501, "zuul.niws.defaultClient or zuul.default.host not defined")
        }

        String uri = RequestContext.currentContext.request.getRequestURI()
        if (RequestContext.currentContext.requestURI != null) {
            uri = RequestContext.currentContext.requestURI
        }
        if (uri == null) uri = "/"
        if (uri.startsWith("/")) {
            uri = uri - "/"
        }

        ((NFRequestContext) RequestContext.currentContext).route = uri.substring(0, uri.indexOf("/") + 1)
    }
}