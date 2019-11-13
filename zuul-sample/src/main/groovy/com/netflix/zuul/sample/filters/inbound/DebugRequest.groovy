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

package com.netflix.zuul.sample.filters.inbound

import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import com.netflix.zuul.message.http.HttpRequestMessage

/**
 *  Add debug request info to the context if request is marked as debug.
 *
 * Author: Arthur Gonigberg
 * Date: December 22, 2017
 */
class DebugRequest extends HttpInboundSyncFilter {

    @Override
    int filterOrder() {
        return 21
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {
        return request.getContext().debugRequest()
    }

    @Override
    boolean needsBodyBuffered(HttpRequestMessage request) {
        return shouldFilter(request)
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {
        SessionContext ctx = request.getContext()

        Debug.addRequestDebug(ctx, "REQUEST:: " + request.getOriginalScheme() + " " + request.getOriginalHost() + ":" + request.getOriginalPort())

        Debug.addRequestDebug(ctx, "REQUEST:: > " + request.getMethod() + " " + request.reconstructURI() + " " + request.getProtocol())

        Iterator headerIt = request.getHeaders().iterator()
        while (headerIt.hasNext()) {
            String name = (String) headerIt.next()
            String value = request.getHeaders().getFirst(name)
            Debug.addRequestDebug(ctx, "REQUEST:: > " + name + ":" + value)

        }

        if (request.hasBody()) {
            Debug.addRequestDebug(ctx, "REQUEST:: > " + request.getBodyAsText())
        }

        return request
    }
}
