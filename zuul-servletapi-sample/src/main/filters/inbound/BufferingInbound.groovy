/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package inbound

import com.netflix.zuul.filters.FilterType
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.ZuulMessage
import com.netflix.zuul.filters.MessageBodyBufferFilter

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 11:32 AM
 */
class BufferingInbound extends MessageBodyBufferFilter
{
    @Override
    boolean shouldFilter(ZuulMessage msg) {
        HttpRequestMessage request = msg
        return request.getQueryParams().getFirst("buffer")
    }

    @Override
    FilterType filterType() {
        return FilterType.INBOUND
    }
}
