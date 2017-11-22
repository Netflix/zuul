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

import com.netflix.zuul.filters.http.HttpInboundSyncFilter
import com.netflix.zuul.message.http.HttpRequestMessage

/**
 * Determine if requests need to be debugged.
 *
 * In order to test this, set query parameter "debugRequest=true"
 *
 * Author: Arthur Gonigberg
 * Date: December 22, 2017
 */
class Debug extends HttpInboundSyncFilter {
    @Override
    int filterOrder() {
        return 20
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {
        return "true".equalsIgnoreCase(request.getQueryParams().getFirst("debugRequest"))
    }

    @Override
    HttpRequestMessage apply(HttpRequestMessage request) {
        request.getContext().setDebugRequest(true)
        request.getContext().setDebugRouting(true)

        return request
    }

}
