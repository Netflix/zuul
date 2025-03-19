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

package com.netflix.zuul.sample.filters.inbound;

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.http.HttpRequestMessage;

/**
 *  Add debug request info to the context if request is marked as debug.
 *
 * Author: Arthur Gonigberg
 * Date: December 22, 2017
 */
public class DebugRequest extends HttpInboundSyncFilter {

    @Override
    public int filterOrder() {
        return 21;
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage request) {
        return request.getContext().debugRequest();
    }

    @Override
    public boolean needsBodyBuffered(HttpRequestMessage request) {
        return shouldFilter(request);
    }

    @Override
    public HttpRequestMessage apply(HttpRequestMessage request) {
        SessionContext ctx = request.getContext();

        Debug.addRequestDebug(
                ctx,
                "REQUEST:: " + request.getOriginalScheme() + " " + request.getOriginalHost() + ":"
                        + request.getOriginalPort());

        Debug.addRequestDebug(
                ctx,
                "REQUEST:: > " + request.getMethod() + " " + request.reconstructURI() + " " + request.getProtocol());

        for (Header header : request.getHeaders().entries()) {
            Debug.addRequestDebug(ctx, "REQUEST:: > " + header.getName() + ":" + header.getValue());
        }

        if (request.hasBody()) {
            Debug.addRequestDebug(ctx, "REQUEST:: > " + request.getBodyAsText());
        }

        return request;
    }
}
