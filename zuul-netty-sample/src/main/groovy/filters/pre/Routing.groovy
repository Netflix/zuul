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
package filters.pre

import com.netflix.zuul.context.Attributes
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.BaseSyncFilter

/**
 * @author Mikey Cohen
 * Date: 1/23/13
 * Time: 2:03 PM
 */
class Routing extends BaseSyncFilter
{
    @Override
    int filterOrder() {
        return 1
    }

    @Override
    String filterType() {
        return "pre"
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return true
    }

    @Override
    SessionContext apply(SessionContext ctx) {

        Attributes attrs = ctx.getAttributes()
        HttpRequestMessage request = ctx.getRequest()

        // Choose vip to proxy to.
        if (request.getPath().startsWith("/yahoo/") || "www.yahoo.com" == request.getHeaders().getFirst("Host")) {
            attrs.setRouteVIP("yahoo")
        }
        else if (request.getPath() == "/blah") {
            // TEST - make use the static filters.
            attrs.setRouteVIP(null)
            attrs.set("static_endpoint", "example")
        }
        else {
            attrs.setRouteVIP("netflix")
        }

        return ctx
    }
}