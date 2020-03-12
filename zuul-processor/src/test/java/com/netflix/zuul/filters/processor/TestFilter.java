/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul.filters.processor;

import com.netflix.zuul.exception.ZuulFilterConcurrencyExceededException;
import com.netflix.zuul.filters.FilterSyncType;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import io.netty.handler.codec.http.HttpContent;
import rx.Observable;

/**
 * A dummy filter which is used for testing.
 */
public abstract class TestFilter implements ZuulFilter<ZuulMessage, ZuulMessage> {

    @Override
    public boolean isDisabled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String filterName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int filterOrder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterType filterType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean overrideStopFilterProcessing() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void incrementConcurrency() throws ZuulFilterConcurrencyExceededException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decrementConcurrency() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterSyncType getSyncType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ZuulMessage getDefaultOutput(ZuulMessage input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean needsBodyBuffered(ZuulMessage input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldFilter(ZuulMessage msg) {
        throw new UnsupportedOperationException();
    }
}
