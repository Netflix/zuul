/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.integration.server.filters;

import com.netflix.zuul.Filter;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import com.netflix.zuul.message.http.HttpRequestMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;

@Filter(order = 0, type = FilterType.INBOUND)
public class InboundRoutesFilter extends HttpInboundSyncFilter {
    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage msg) {
        return true;
    }

    @Override
    public HttpRequestMessage apply(HttpRequestMessage input) {
        // uncomment this line to trigger a resource leak
        // ByteBuf buffer = UnpooledByteBufAllocator.DEFAULT.buffer();

        SessionContext context = input.getContext();
        context.setEndpoint(ProxyEndpoint.class.getCanonicalName());
        context.setRouteVIP("api");
        return input;
    }
}
