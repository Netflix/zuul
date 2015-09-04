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

import com.netflix.zuul.context.Debug
import com.netflix.zuul.filters.http.HttpInboundFilter
import com.netflix.zuul.message.http.HttpRequestMessage
import rx.Observable

/**
 * @author Mikey Cohen
 * Date: 3/12/12
 * Time: 1:51 PM
 */
class DebugRequest extends HttpInboundFilter
{
    @Override
    int filterOrder() {
        return 10000
    }

    @Override
    boolean shouldFilter(HttpRequestMessage msg) {
        return Debug.debugRequest(msg.getContext())

    }

    @Override
    Observable<HttpRequestMessage> applyAsync(HttpRequestMessage req)
    {
        return Debug.writeDebugRequest(req.getContext(), req.getInboundRequest(), true)
                .map({v -> req})
    }
}
