/*
 *
 *  Copyright 2013-2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.zuul;

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import rx.Observable;
import rx.functions.Func1;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;

import static com.netflix.zuul.ExecutionStatus.*;


/**
 * Experimenting with how to optimise use of Observable/Single when not required (ie. for Sync filters).
 *
 * Also need to find a way to break out of filter chain processing (ie. when choose to throttle or reject requests) more
 * efficiently, as current mechanism is not shedding much load.
 *
 * @author Mike Smith
 */
@Singleton
public class ExperimentalFilterProcessor2 extends FilterProcessor
{
    @Inject
    public ExperimentalFilterProcessor2(FilterLoader loader, FilterUsageNotifier usageNotifier) {
        super(loader, usageNotifier);
    }

    @Override
    public Observable<ZuulMessage> applyFilterChain(ZuulMessage msg)
    {
        Iterator<ZuulFilter> filterChainIterator = createFilterChainIterator();
        return chainFilters(msg, filterChainIterator);
    }

    public Iterator<ZuulFilter> createFilterChainIterator()
    {
        ArrayList<ZuulFilter> filterChain = new ArrayList<>();
        filterChain.addAll(filterLoader.getFiltersByType("in"));
        // TODO - need to handle endpoint specially?
        filterChain.addAll(filterLoader.getFiltersByType("end"));
        filterChain.addAll(filterLoader.getFiltersByType("out"));
        return filterChain.iterator();
    }

    public Observable<ZuulMessage> chainFilters(ZuulMessage msg, final Iterator<ZuulFilter> filterChainIterator)
    {
        if (! filterChainIterator.hasNext()) {
            // TODO - isn't there a better way of doing this without having to wrap in an Observable again?
            return Observable.just(msg);
        }

        ZuulFilter filter = filterChainIterator.next();

        // TODO - move to be a method in ZuulFilter.getDefaultResult().
        Func1<ZuulMessage, ZuulMessage> defaultFilterResultChooser = (input) -> input;

        if (BaseSyncFilter.class.isAssignableFrom(filter.getClass())) {
            msg = processSyncFilter(msg, (BaseSyncFilter) filter, defaultFilterResultChooser);
            return chainFilters(msg, filterChainIterator);
        }
        else {
            return processAsyncFilter(msg, filter, defaultFilterResultChooser)
                    .flatMap(msg2 -> {
                        return chainFilters(msg2, filterChainIterator);
                    });
        }
    }

    /**
     * TODO - find a way to remove some of the duplication between this method and processAsyncFilter().
     *
     * @param msg
     * @param filter
     * @param defaultFilterResultChooser
     * @return
     */
    public ZuulMessage processSyncFilter(ZuulMessage msg, BaseSyncFilter filter,
                                         Func1<ZuulMessage, ZuulMessage> defaultFilterResultChooser)
    {
        final FilterExecInfo info = new FilterExecInfo();
        info.bDebug = msg.getContext().debugRouting();

        if (info.bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = msg.clone();
        }

        // Apply this filter.
        ZuulMessage result;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                result = defaultFilterResultChooser.call(msg);
                info.status = DISABLED;
            }
            else if (msg.getContext().shouldStopFilterProcessing()) {
                // This is typically set by a filter when wanting to reject a request, and also reduce load on the server by
                // not processing any more filters.
                result = defaultFilterResultChooser.call(msg);
                info.status = SKIPPED;
            }
            else {
                // Only apply the filter if both the shouldFilter() method AND the filter has a priority of
                // equal or above the requested.
                int requiredPriority = msg.getContext().getFilterPriorityToApply();
                if (isFilterPriority(filter, requiredPriority) && filter.shouldFilter(msg)) {
                    result = filter.apply(msg);

                    // If no result returned from filter, then use the original input.
                    if (result == null) {
                        result = defaultFilterResultChooser.call(msg);
                    }
                }
                else {
                    result = defaultFilterResultChooser.call(msg);
                    info.status = SKIPPED;
                }
            }
        }
        catch (Exception e) {
            result = defaultFilterResultChooser.call(msg);
            msg.getContext().setError(e);
            info.status = FAILED;
            recordFilterError(filter, msg, e);
        }

        // Record info when filter processing completes.
        if (info.status == null) {
            info.status = SUCCESS;
        }
        info.execTime = System.currentTimeMillis() - ltime;
        recordFilterCompletion(result, filter, info);

        return result;
    }
}
