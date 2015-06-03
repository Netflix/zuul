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
package com.netflix.zuul.filters;

import com.netflix.zuul.context.ZuulMessage;
import rx.Observable;

/**
 * BAse interface for ZuulFilters
 *
 * @author Mikey Cohen
 *         Date: 10/27/11
 *         Time: 3:03 PM
 */
public interface ZuulFilter<I extends ZuulMessage, O extends ZuulMessage> extends ShouldFilter<I>
{
    public boolean isDisabled();

    public String filterName();

    /**
     * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
     * important for a filter. filterOrders do not need to be sequential.
     *
     * @return the int order of a filter
     */
    int filterOrder();

    /**
     * to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
     * "route" for routing to an origin, "post" for post-routing filters, "error" for error handling.
     * We also support a "static" type for static responses see  StaticResponseFilter.
     * Any filterType made be created or added and run by calling FilterProcessor.runFilters(type)
     *
     * @return A String representing that type
     */
    String filterType();

    /**
     * if shouldFilter() is true, this method will be invoked. this method is the core method of a ZuulFilter
     */
    Observable<O> applyAsync(I input);
}
