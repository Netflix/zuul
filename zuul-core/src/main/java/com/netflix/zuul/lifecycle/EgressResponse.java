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
import io.reactivex.netty.protocol.http.client.HttpResponseHeaders;
import rx.Observable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EgressResponse<T> {
    private final HttpResponseStatus status;
    private final Map<String, List<String>> headers;
    private final Observable<ByteBuf> content;
    private final T state;

    private EgressResponse(HttpResponseStatus status, Map<String, List<String>> headers, Observable<ByteBuf> content, T state) {
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
        List<String> currentHeaderList = headers.get(name);
        if (currentHeaderList == null) {
            headers.put(name, Arrays.asList(value));
        } else {
            currentHeaderList.add(value);
            headers.put(name, currentHeaderList);
        }
    }
    
    public T get() {
        return state;
    }

    public static <T> EgressResponse<T> withStatus(int statusCode) {
        return new EgressResponse<>(HttpResponseStatus.valueOf(statusCode), new HashMap<>(), Observable.empty(), null);
    }

    public static <T> EgressResponse<T> from(int statusCode, Map<String, List<String>> headerMap, Observable<ByteBuf> content) {
        HttpResponseStatus nettyStatus = HttpResponseStatus.valueOf(statusCode);
        return new EgressResponse<>(nettyStatus, headerMap, content, null);
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    private static Map<String, List<String>> convertHeaders(HttpResponseHeaders clientHeaders) {
        Map<String, List<String>> headers = new HashMap<>();
        for (String key: clientHeaders.names()) {
            headers.put(key, clientHeaders.getAll(key));
        }
        return headers;
    }
}
