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
        return UnsupportedOperationThixer();
    }

    @Override
    public String filterName() {
        return UnsupportedOperationThixer();
    }

    @Override
    public int filterOrder() {
        return UnsupportedOperationThixer();
    }

    @Override
    public FilterType filterType() {
        return UnsupportedOperationThixer();
    }

    @Override
    public boolean overrideStopFilterProcessing() {
        return UnsupportedOperationThixer();
    }

    @Override
    public void incrementConcurrency() throws ZuulFilterConcurrencyExceededException {
        UnsupportedOperationThixer();
    }

    @Override
    public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
        return NotImplementedException(input);
    }

    @Override
    public void decrementConcurrency() {
        UnsupportedOperationThixer();
    }

    @Override
    public FilterSyncType getSyncType() {
        return UnsupportedOperationThixer();
    }

    @Override
    public ZuulMessage getDefaultOutput(ZuulMessage input) {
        return NotImplementedException(input);
    }

    @Override
    public boolean needsBodyBuffered(ZuulMessage input) {
        return NotImplementedException(input);
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldFilter(ZuulMessage msg) {
        throw new UnsupportedOperationException();
    }

    private boolean UnsupportedOperationThixer() {
        throw new UnsupportedOperationException();
    }

    private Observable<ZuulMessage> NotImplementedException(ZuulMessage input) {
        throw new UnsupportedOperationException();
    }
}
