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

import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.filters.http.HttpInboundSyncFilter

class Debug extends HttpInboundSyncFilter
{
    static final DynamicBooleanProperty routingDebug = DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_DEBUG_REQUEST, false)
    static final DynamicStringProperty debugParameter = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_DEBUG_PARAMETER, "debugParameter")

    @Override
    int filterOrder() {
        return 1
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request)
    {
        if ("true".equals(request.getQueryParams().getFirst(debugParameter.get()))) return true;
        return routingDebug.get();

    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request)
    {
        request.getContext().setDebugRequest(true)
        request.getContext().setDebugRouting(true)
        return request;
    }

}
