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

import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.filters.http.HttpInboundSyncFilter;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 7:31 PM
 */
public class MockHttpSyncInboundFilter extends HttpInboundSyncFilter
{
    private String filterName;
    private int filterOrder;
    private boolean shouldFilter;
    private Throwable error;
    private boolean shouldSendErrorResponse;
    private boolean shouldStopFilterProcessing;

    public MockHttpSyncInboundFilter(int filterOrder, boolean shouldFilter)
    {
        this("MockHttpSyncInboundFilter-" + filterOrder, filterOrder, shouldFilter);
    }

    public MockHttpSyncInboundFilter(String filterName, int filterOrder, boolean shouldFilter)
    {
        this(filterName, filterOrder, shouldFilter, null, false);
    }

    public MockHttpSyncInboundFilter(String filterName, int filterOrder, boolean shouldFilter,
                                     Throwable error, boolean shouldSendErrorResponse)
    {
        this.filterName = filterName;
        this.filterOrder = filterOrder;
        this.shouldFilter = shouldFilter;
        this.error = error;
        this.shouldSendErrorResponse = shouldSendErrorResponse;
    }

    public void setShouldStopFilterProcessing(boolean shouldStopFilterProcessing)
    {
        this.shouldStopFilterProcessing = shouldStopFilterProcessing;
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
        return filterOrder;
    }

    @Override
    public HttpRequestMessage apply(HttpRequestMessage input)
    {
        if (shouldStopFilterProcessing) {
            input.getContext().stopFilterProcessing();
        }

        if (error != null) {
            if (shouldSendErrorResponse) input.getContext().setShouldSendErrorResponse(true);
            throw new RuntimeException("Some error response problem.");
        }
        else {
            return input;
        }
    }
}
