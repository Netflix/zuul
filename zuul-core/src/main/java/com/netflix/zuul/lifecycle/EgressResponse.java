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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpResponseHeaders;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class EgressResponse<T> {
    private final HttpResponseStatus status;
    private final Map<String, String> headers;
    private final Observable<ByteBuf> content;
    private final T state;

    private EgressResponse(HttpResponseStatus status, Map<String, String> headers, Observable<ByteBuf> content, T state) {
        this.status = status;
        this.headers = headers;
        this.content = content;
        this.state = state;
    }

    public static <T> EgressResponse<T> from(IngressResponse<T> ingressResp) {
        return new EgressResponse<T>(ingressResp.getStatus(), convertHeaders(ingressResp.getHeaders()), ingressResp.getContent(), ingressResp.getState());
    }

    public Observable<ByteBuf> getContent() {
        return content;
    }

    public void addHeader(String name, String value) {
        headers.put(name, value);
    }
    
    public T get() {
        return state;
    }

    public static <T> EgressResponse<T> withStatus(int statusCode) {
        return new EgressResponse<>(HttpResponseStatus.valueOf(statusCode), new HashMap<>(), Observable.empty(), null);
    }

    public static <T> EgressResponse<T> from(int statusCode, Map<String, String> headerMap, String content) {
        HttpResponseStatus nettyStatus = HttpResponseStatus.valueOf(statusCode);
        Observable<ByteBuf> nettyContent = Observable.from(content).map(s -> Unpooled.copiedBuffer(s, Charset.defaultCharset()));
        return new EgressResponse<>(nettyStatus, headerMap, nettyContent, null);
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    private static Map<String, String> convertHeaders(HttpResponseHeaders clientHeaders) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry: clientHeaders.entries()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        return headers;
    }
}
