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

package com.netflix.zuul.filters.http;

import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 10:36 PM
 */
public abstract class HttpAsyncEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    @Override
    public HttpResponseMessage getDefaultOutput(HttpRequestMessage request)
    {
        return HttpResponseMessageImpl.defaultErrorResponse(request);
    }
}
