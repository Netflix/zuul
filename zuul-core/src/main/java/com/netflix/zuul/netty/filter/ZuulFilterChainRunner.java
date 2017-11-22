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

package com.netflix.zuul.netty.filter;

import com.netflix.spectator.impl.Preconditions;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.handler.codec.http.HttpContent;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is supposed to be thread safe and hence should not have any non final member variables
 * Created by saroskar on 5/17/17.
 */
@ThreadSafe
public class ZuulFilterChainRunner<T extends ZuulMessage> extends BaseZuulFilterRunner<T, T> {

    private final ZuulFilter<T, T>[] filters;

    public ZuulFilterChainRunner(ZuulFilter<T, T>[] zuulFilters, FilterUsageNotifier usageNotifier, FilterRunner<T, ?> nextStage) {
        super(zuulFilters[0].filterType(), usageNotifier, nextStage);
        this.filters = zuulFilters;
    }

    public ZuulFilterChainRunner(ZuulFilter<T, T>[] zuulFilters, FilterUsageNotifier usageNotifier) {
        this(zuulFilters, usageNotifier, null);
    }

    @Override
    public void filter(final T inMesg) {
        runFilters(inMesg, initRunningFilterIndex(inMesg));
    }

    @Override
    protected void resume(final T inMesg) {
        final AtomicInteger runningFilterIdx = getRunningFilterIndex(inMesg);
        runningFilterIdx.incrementAndGet();
        runFilters(inMesg, runningFilterIdx);
    }

    private final void runFilters(final T mesg, final AtomicInteger runningFilterIdx) {
        T inMesg = mesg;
        String filterName = "-";
        try {
            Preconditions.checkNotNull(mesg, "Input message");
            int i = runningFilterIdx.get();

            while (i < filters.length) {
                final ZuulFilter<T, T> filter = filters[i];
                filterName = filter.filterName();
                final T outMesg = filter(filter, inMesg);
                if (outMesg == null) {
                    return; //either async filter or waiting for the message body to be buffered
                }
                inMesg = outMesg;
                i = runningFilterIdx.incrementAndGet();
            }

            //Filter chain has reached its end, pass result to the next stage
            invokeNextStage(inMesg);
        }
        catch (Exception ex) {
            handleException(inMesg, filterName, ex);
        }
    }

    @Override
    public void filter(T inMesg, HttpContent chunk) {
        String filterName = "-";
        try {
            Preconditions.checkNotNull(inMesg, "input message");

            final AtomicInteger runningFilterIdx = getRunningFilterIndex(inMesg);
            final int limit = runningFilterIdx.get();
            for (int i = 0; i < limit; i++) {
                final ZuulFilter<T, T> filter = filters[i];
                filterName = filter.filterName();
                if ((! filter.isDisabled()) && (! shouldSkipFilter(inMesg, filter))) {
                    final HttpContent newChunk = filter.processContentChunk(inMesg, chunk);
                    if (newChunk == null)  {
                        //Filter wants to break the chain and stop propagating this chunk any further
                        return;
                    }
                    //deallocate original chunk if necessary
                    if ((newChunk != chunk) && (chunk.refCnt() > 0)) {
                        chunk.release(chunk.refCnt());
                    }
                    chunk = newChunk;
                }
            }

            if (limit >= filters.length) {
                //Filter chain has run to end, pass down the channel pipeline
                invokeNextStage(inMesg, chunk);
            } else {
                inMesg.bufferBodyContents(chunk);

                boolean isAwaitingBody = isFilterAwaitingBody(inMesg);

                // Record passport states for start and end of buffering bodies.
                if (isAwaitingBody) {
                    CurrentPassport passport = CurrentPassport.fromSessionContext(inMesg.getContext());
                    if (inMesg.hasCompleteBody()) {
                        if (inMesg instanceof HttpRequestMessage) {
                            passport.addIfNotAlready(PassportState.FILTERS_INBOUND_BUF_END);
                        } else if (inMesg instanceof HttpResponseMessage) {
                            passport.addIfNotAlready(PassportState.FILTERS_OUTBOUND_BUF_END);
                        }
                    }
                    else {
                        if (inMesg instanceof HttpRequestMessage) {
                            passport.addIfNotAlready(PassportState.FILTERS_INBOUND_BUF_START);
                        } else if (inMesg instanceof HttpResponseMessage) {
                            passport.addIfNotAlready(PassportState.FILTERS_OUTBOUND_BUF_START);
                        }
                    }
                }

                if (isAwaitingBody && inMesg.hasCompleteBody()) {
                    //whole body has arrived, resume filter chain
                    runFilters(inMesg, runningFilterIdx);
                }
            }
        }
        catch (Exception ex) {
            handleException(inMesg, filterName, ex);
        }
    }

}
