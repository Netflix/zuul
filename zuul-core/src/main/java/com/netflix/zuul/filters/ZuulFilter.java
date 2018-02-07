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

import com.netflix.zuul.exception.ZuulFilterConcurrencyExceededException;
import com.netflix.zuul.message.ZuulMessage;
import io.netty.handler.codec.http.HttpContent;
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
    boolean isDisabled();

    String filterName();

    /**
     * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
     * important for a filter. filterOrders do not need to be sequential.
     *
     * @return the int order of a filter
     */
    int filterOrder();

    /**
     * to classify a filter by type. Standard types in Zuul are "in" for pre-routing filtering,
     * "end" for routing to an origin, "out" for post-routing filters.
     *
     * @return FilterType
     */
    FilterType filterType();

    /**
     * Whether this filter's shouldFilter() method should be checked, and apply() called, even
     * if SessionContext.stopFilterProcessing has been set.
     *
     * @return boolean
     */
    boolean overrideStopFilterProcessing();

    /**
     * Called by zuul filter runner before sending request through this filter. The filter can throw
     * ZuulFilterConcurrencyExceededException if it has reached its concurrent requests limit and does
     * not wish to process the request. Generally only useful for async filters.
     */
    void incrementConcurrency() throws ZuulFilterConcurrencyExceededException;

    /**
     * if shouldFilter() is true, this method will be invoked. this method is the core method of a ZuulFilter
     */
    Observable<O> applyAsync(I input);

    /**
     * Called by zuul filter after request is processed by this filter.
     *
     */
    void decrementConcurrency();

    FilterSyncType getSyncType();

    /**
     * Choose a default message to use if the applyAsync() method throws an exception.
     *
     * @return ZuulMessage
     */
    O getDefaultOutput(I input);

    /**
     * Filter indicates it needs to read and buffer whole body before it can operate on the messages by returning true.
     * The decision can be made at runtime, looking at the request type. For example if the incoming message is a MSL
     * message MSL decryption filter can return true here to buffer whole MSL message before it tries to decrypt it.
     * @return true if this filter needs to read whole body before it can run, false otherwise
     */
    boolean needsBodyBuffered(I input);

    /**
     * Optionally transform HTTP content chunk received
     * @param chunk
     * @return
     */
    HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk);
}
