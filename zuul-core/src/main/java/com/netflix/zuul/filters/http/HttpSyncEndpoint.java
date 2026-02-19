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
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.filters.SyncZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.concurrent.CompletableFuture;

/**
 * User: Mike Smith
 * Date: 6/16/15
 * Time: 12:23 AM
 */
public abstract class HttpSyncEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessage>
        implements SyncZuulFilter<HttpRequestMessage, HttpResponseMessage> {
    // Feature flag for enabling this while we get some real data for the impact.
    private static final CachedDynamicBooleanProperty WAIT_FOR_LASTCONTENT =
            new CachedDynamicBooleanProperty("zuul.endpoint.sync.wait_for_lastcontent", true);

    private static final SessionContext.Key<ResponseState> KEY_FOR_SUBSCRIBER =
            SessionContext.newKey("_HttpSyncEndpoint_subscriber");

    @Override
    public HttpResponseMessage getDefaultOutput(HttpRequestMessage request) {
        return HttpResponseMessageImpl.defaultErrorResponse(request);
    }

    @Override
    public CompletableFuture<HttpResponseMessage> applyAsync(HttpRequestMessage input) {
        if (WAIT_FOR_LASTCONTENT.get() && !input.hasCompleteBody()) {
            // defer completion until we have received the full request body from the client,
            // so that we can't potentially corrupt the clients' http state on this connection
            HttpResponseMessage response = this.apply(input);
            CompletableFuture<HttpResponseMessage> future = new CompletableFuture<>();
            input.getContext().put(KEY_FOR_SUBSCRIBER, new ResponseState(response, future));
            return future;
        } else {
            return CompletableFuture.completedFuture(this.apply(input));
        }
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
        if (chunk instanceof LastHttpContent) {
            ResponseState state = zuulMessage.getContext().get(KEY_FOR_SUBSCRIBER);
            if (state != null) {
                state.future.complete(state.response);
                zuulMessage.getContext().remove(KEY_FOR_SUBSCRIBER);
            }
        }
        return super.processContentChunk(zuulMessage, chunk);
    }

    @Override
    public void incrementConcurrency() {
        // NOOP, since this is supposed to be a SYNC filter in spirit
    }

    @Override
    public void decrementConcurrency() {
        // NOOP, since this is supposed to be a SYNC filter in spirit
    }

    private record ResponseState(HttpResponseMessage response, CompletableFuture<HttpResponseMessage> future) {}
}
