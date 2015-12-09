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

import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import com.netflix.zuul.routing.Route
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Mikey Cohen
 * Date: 1/23/13
 * Time: 2:03 PM
 */
class Routing extends HttpInboundSyncFilter
{
    protected static final Logger LOG = LoggerFactory.getLogger(Routing.class);

    @Override
    int filterOrder() {
        return 10
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {
        return true
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {

        SessionContext context = request.getContext()

        // Choose vip to proxy to.
        if (request.getPath().startsWith("/yahoo/") || "www.yahoo.com" == request.getHeaders().getFirst("Host")) {
            context.getRoutingResult().assign(new Route("yahoo"), Routing.class.getName())
            context.setEndpoint("endpoint.ZuulNFRequest")
        }
        else if (request.getPath() == "/blah") {
            // TEST - make use the static filters.
            context.setEndpoint("endpoint.ExampleStaticFilter")
        }
        else if (request.getPath() == "/simulator/") {
            context.getRoutingResult().assign(new Route("simulator"), Routing.class.getName())
            context.setEndpoint("endpoint.ZuulNFRequest")
        }
        else {
            context.getRoutingResult().assign(new Route("api_netflix"), Routing.class.getName())
            context.setEndpoint("endpoint.ZuulNFRequest")
        }

        return request
    }
}