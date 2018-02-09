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

import static com.netflix.zuul.filters.FilterSyncType.SYNC;
import static com.netflix.zuul.filters.FilterType.ENDPOINT;

/**
 * Base class to help implement SyncZuulFilter. Note that the class BaseSyncFilter does exist but it derives from
 * BaseFilter which in turn creates a new instance of CachedDynamicBooleanProperty for "filterDisabled" every time you
 * create a new instance of the ZuulFilter. Normally it is not too much of a concern as the instances of ZuulFilters
 * are "effectively" singleton and are cached by ZuulFilterLoader. However, if you ever have a need for instantiating a
 * new ZuulFilter instance per request - aka EdgeProxyEndpoint or Inbound/Outbound PassportStampingFilter creating new
 * instances of CachedDynamicBooleanProperty per instance of ZuulFilter will quickly kill your server's performance in
 * two ways -
 * a) Instances of CachedDynamicBooleanProperty are *very* heavy CPU wise to create due to extensive hookups machinery
 *    in their constructor
 * b) They leak memory as they add themselves to some ConcurrentHashMap and are never garbage collected.
 *
 * TL;DR use this as a base class for your ZuulFilter if you intend to create new instances of ZuulFilter
 * Created by saroskar on 6/8/17.
 */
public abstract class SyncZuulFilterAdapter<I extends ZuulMessage, O extends ZuulMessage> implements SyncZuulFilter<I, O> {

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public boolean shouldFilter(I msg) {
        return true;
    }

    @Override
    public int filterOrder() {
        // Set all Endpoint filters to order of 0, because they are not processed sequentially like other filter types.
        return 0;
    }

    @Override
    public FilterType filterType() {
        return ENDPOINT;
    }

    @Override
    public boolean overrideStopFilterProcessing() {
        return false;
    }

    @Override
    public Observable<O> applyAsync(I input) {
        return Observable.just(apply(input));
    }

    @Override
    public FilterSyncType getSyncType() {
        return SYNC;
    }

    @Override
    public boolean needsBodyBuffered(I input) {
        return false;
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
        return chunk;
    }

    @Override
    public void incrementConcurrency() {
        //NOOP for sync filters
    }

    @Override
    public void decrementConcurrency() {
        //NOOP for sync filters
    }
}