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

package com.netflix.zuul.message.http;

import com.netflix.zuul.filters.http.HttpAsyncEndpoint;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:33 PM
 */
public class MockEndpointFilter extends HttpAsyncEndpoint
{
    private String filterName;
    private boolean shouldFilter;
    private HttpResponseMessage response;
    private Throwable error = null;

    public MockEndpointFilter(boolean shouldFilter)
    {
        this("MockEndpointFilter", shouldFilter, null);
    }

    public MockEndpointFilter(boolean shouldFilter, HttpResponseMessage response)
    {
        this("MockEndpointFilter", shouldFilter, response);
    }

    public MockEndpointFilter(String filterName, boolean shouldFilter, HttpResponseMessage response)
    {
        this.filterName = filterName;
        this.shouldFilter = shouldFilter;
        this.response = response;
    }

    public MockEndpointFilter(String filterName, boolean shouldFilter, HttpResponseMessage response, Throwable error)
    {
        this.filterName = filterName;
        this.shouldFilter = shouldFilter;
        this.response = response;
        this.error = error;
    }

    @Override
    public String filterName()
    {
        return filterName;
    }

    @Override
    public boolean shouldFilter(HttpRequestMessage msg)
    {
        return shouldFilter;
    }

    @Override
    public int filterOrder()
    {
        return 0;
    }

    @Override
    public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input)
    {
        if (error != null) {
            return Observable.create(subscriber -> {
                Throwable t = new RuntimeException("Some error response problem.");
                subscriber.onError(t);
            });
        }
        else if (response != null) {
            return Observable.just(response);
        }
        else {
            return Observable.just(new HttpResponseMessageImpl(input.getContext(), input, 200));
        }
    }
}
