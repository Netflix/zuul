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
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.integration.server.HeaderNames;
import com.netflix.zuul.message.http.HttpRequestMessage;
import java.util.UUID;
import rx.Observable;

@Filter(order = 10, type = FilterType.INBOUND)
public class RequestHeaderFilter extends HttpInboundFilter {
    @Override
    public boolean shouldFilter(HttpRequestMessage msg) {
        return true;
    }

    @Override
    public Observable<HttpRequestMessage> applyAsync(HttpRequestMessage request) {
        request.getHeaders().set(HeaderNames.REQUEST_ID, "RQ-" + UUID.randomUUID());
        request.storeInboundRequest();
        return Observable.just(request);
    }
}
