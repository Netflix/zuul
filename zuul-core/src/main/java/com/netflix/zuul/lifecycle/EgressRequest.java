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
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class EgressRequest<T> {
    private final static Logger logger = LoggerFactory.getLogger(EgressRequest.class);

    private HttpClientRequest<ByteBuf> httpClientRequest;
    private final HttpServerRequest<ByteBuf> httpServerRequest;
    private final T state;
    
    private EgressRequest(HttpClientRequest<ByteBuf> httpClientRequest, HttpServerRequest<ByteBuf> httpServerRequest, T state) {
        this.httpClientRequest = httpClientRequest;
        this.httpServerRequest = httpServerRequest;
        this.state = state;
    }

    public static <T> EgressRequest<T> copiedFrom(IngressRequest ingressReq, T requestState) {
        HttpServerRequest<ByteBuf> nettyReq = ingressReq.getHttpServerRequest();
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(nettyReq.getHttpMethod(), nettyReq.getUri());
        for (Map.Entry<String, String> entry: nettyReq.getHeaders().entries()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Adding header to EgressRequest : " + entry.getKey() + " -> " + entry.getValue());
            }
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }
        //TODO add this back with appropriate eager subscription
        clientReq = clientReq.withContentSource(nettyReq.getContent().map(ByteBuf::retain));
        return new EgressRequest<>(clientReq, ingressReq.getHttpServerRequest(), requestState);
    }

    public HttpClientRequest<ByteBuf> getHttpClientRequest() {
        return httpClientRequest;
    }

    public HttpServerRequest<ByteBuf> getHttpServerRequest() {
        return httpServerRequest;
    }
    
    public T get() {
        return state;
    }
}
