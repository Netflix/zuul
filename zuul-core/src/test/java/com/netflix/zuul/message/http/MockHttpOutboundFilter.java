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

import com.netflix.zuul.filters.http.HttpOutboundFilter;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:32 PM
 */
public class MockHttpOutboundFilter extends HttpOutboundFilter
{
    private String filterName;
    private int filterOrder;
    private boolean shouldFilter;

    public MockHttpOutboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpOutboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpOutboundFilter(String filterName, int filterOrder, boolean shouldFilter)
    {
        this.filterName = filterName;
        this.filterOrder = filterOrder;
        this.shouldFilter = shouldFilter;
    }

    @Override
    public String filterName()
    {
        return filterName;
    }

    @Override
    public boolean shouldFilter(HttpResponseMessage msg)
    {
        return shouldFilter;
    }

    @Override
    public int filterOrder()
    {
        return filterOrder;
    }

    @Override
    public Observable<HttpResponseMessage> applyAsync(HttpResponseMessage input)
    {
        return Observable.just(input);
    }
}
