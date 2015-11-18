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
package endpoint

import com.netflix.zuul.filters.http.HttpAsyncEndpoint
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import rx.Observable

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 1:53 PM
 */
class ExampleStaticFilter extends HttpAsyncEndpoint
{
    @Override
    Observable<HttpResponseMessageImpl> applyAsync(HttpRequestMessage request)
    {
        HttpResponseMessageImpl response = new HttpResponseMessageImpl(request.getContext(), request, 200)

        response.setStatus(200)
        response.getHeaders().set("Content-Type", "text/plain")
        response.setBody("blah blah\n".getBytes("UTF-8"))

        return Observable.just(response)
    }
}
