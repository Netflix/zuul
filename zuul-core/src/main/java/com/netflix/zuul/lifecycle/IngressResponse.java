/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.lifecycle;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.HttpResponseHeaders;
import rx.Observable;

/**
 * Wrapper around response from origin
 * @param <T>
 */
public class IngressResponse<T> {
    private final HttpResponseHeaders headers;
    private final HttpResponseStatus status;
    private final Observable<ByteBuf> content;
    private final T state;

    protected IngressResponse(HttpResponseHeaders headers, HttpResponseStatus status, Observable<ByteBuf> content, T state) {
        this.headers = headers;
        this.status = status;
        this.content = content;
        this.state = state;
    }

    public HttpResponseHeaders getHeaders() {
        return this.headers;
    }

    public HttpResponseStatus getStatus() {
        return this.status;
    }

    public Observable<ByteBuf> getContent() {
        return this.content;
    }

    public static <T> IngressResponse from(HttpClientResponse<ByteBuf> nettyResponse, T state) {
        return new IngressResponse<>(nettyResponse.getHeaders(), nettyResponse.getStatus(), nettyResponse.getContent(), state);
    }

    public T getState() {
        return this.state;
    }
}
