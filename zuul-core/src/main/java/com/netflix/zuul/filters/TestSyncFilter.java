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
package com.netflix.zuul.filters;

import com.netflix.zuul.message.http.HttpRequestMessage;

/**
 * User: michaels@netflix.com
 * Date: 5/15/15
 * Time: 10:54 AM
 */
public class TestSyncFilter extends BaseSyncFilter<HttpRequestMessage, HttpRequestMessage>
{
    @Override
    public HttpRequestMessage apply(HttpRequestMessage input) {
        return input;
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public FilterType filterType() {
        return FilterType.INBOUND;
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage msg) {
        return true;
    }
}
