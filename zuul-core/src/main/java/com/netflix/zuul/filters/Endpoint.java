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

import com.netflix.zuul.message.ZuulMessage;

/**
 * User: Mike Smith
 * Date: 5/16/15
 * Time: 1:57 PM
 */
public abstract class Endpoint<I extends ZuulMessage, O extends ZuulMessage> extends BaseFilter<I, O>
{
    @Override
    public int filterOrder()
    {
        // Set all Endpoint filters to order of 0, because they are not processed sequentially like other filter types.
        return 0;
    }

    @Override
    public FilterType filterType()
    {
        return FilterType.ENDPOINT;
    }

    @Override
    public boolean shouldFilter(I msg)
    {
        // Always true, because Endpoint filters are chosen by name instead.
        return true;
    }
}
