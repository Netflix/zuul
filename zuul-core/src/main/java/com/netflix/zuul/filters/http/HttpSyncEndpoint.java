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

package com.netflix.zuul.filters.http;

import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.zuul.exception.ZuulFilterConcurrencyExceededException;
import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.filters.SyncZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import rx.Observable;
import rx.Subscriber;

/**
 * User: Mike Smith
 * Date: 6/16/15
 * Time: 12:23 AM
 */
public abstract class HttpSyncEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessage> implements SyncZuulFilter<HttpRequestMessage, HttpResponseMessage>
{
    // Feature flag for enabling this while we get some real data for the impact.
    private static final CachedDynamicBooleanProperty WAIT_FOR_LASTCONTENT = new CachedDynamicBooleanProperty("zuul.endpoint.sync.wait_for_lastcontent", true);

    private static final String KEY_FOR_SUBSCRIBER = "_HttpSyncEndpoint_subscriber";

    @Override
    public HttpResponseMessage getDefaultOutput(HttpRequestMessage request)
    {
        return HttpResponseMessageImpl.defaultErrorResponse(request);
    }

    @Override
    public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input)
    {
        if (WAIT_FOR_LASTCONTENT.get() && ! input.hasCompleteBody()) {
            // Return an observable that won't complete until after we have received the LastContent from client (ie. that we've
            // received the whole request body), so that we can't potentially corrupt the clients' http state on this connection.
            return Observable.create(subscriber -> {
                ZuulMessage response = this.apply(input);
                ResponseState state = new ResponseState(response, subscriber);
                input.getContext().set(KEY_FOR_SUBSCRIBER, state);
            });
        }
        else {
            return Observable.just(this.apply(input));
        }
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk)
    {
        // Only call onNext() after we've received the LastContent of request from client.
        if (chunk instanceof LastHttpContent) {
            ResponseState state = (ResponseState) zuulMessage.getContext().get(KEY_FOR_SUBSCRIBER);
            if (state != null) {
                state.subscriber.onNext(state.response);
                state.subscriber.onCompleted();
                zuulMessage.getContext().remove(KEY_FOR_SUBSCRIBER);
            }
        }
        return super.processContentChunk(zuulMessage, chunk);
    }

    @Override
    public void incrementConcurrency() {
        //NOOP, since this is supposed to be a SYNC filter in spirit
    }

    @Override
    public void decrementConcurrency() {
        //NOOP, since this is supposed to be a SYNC filter in spirit
    }

    private static class ResponseState
    {
        final ZuulMessage response;
        final Subscriber subscriber;

        public ResponseState(ZuulMessage response, Subscriber subscriber)
        {
            this.response = response;
            this.subscriber = subscriber;
        }
    }
}
