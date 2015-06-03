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
package filters.endpoint

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.filters.Endpoint
import rx.Observable

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 1:53 PM
 */
class ExampleStaticFilter extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    @Override
    Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        HttpResponseMessage response = new HttpResponseMessage(request.getContext(), request, 200)

        response.setStatus(200)
        response.getHeaders().set("Content-Type", "text/plain")
        response.setBody("blah blah\n".getBytes("UTF-8"))

        return Observable.just(response)
    }
}
