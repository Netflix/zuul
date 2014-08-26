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
package com.netflix.zuul;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import rx.functions.Action1;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EgressRequest<T> {
    private HttpClientRequest<ByteBuf> httpClientRequest;
    private final HttpServerRequest<ByteBuf> httpServerRequest;
    private final ChannelHandlerContext channelHandlerContext;
    private final T state;
    
    private EgressRequest(HttpClientRequest<ByteBuf> httpClientRequest, HttpServerRequest<ByteBuf> httpServerRequest, ChannelHandlerContext channelHandlerContext, T state) {
        this.httpClientRequest = httpClientRequest;
        this.httpServerRequest = httpServerRequest;
        this.channelHandlerContext = channelHandlerContext;
        this.state = state;
    }

    public static <T> EgressRequest<T> copiedFrom(IngressRequest ingressReq, T requestState) {
        HttpServerRequest<ByteBuf> nettyReq = ingressReq.getHttpServerRequest();
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(nettyReq.getHttpMethod(), nettyReq.getUri());
        for (Map.Entry<String, String> entry: nettyReq.getHeaders().entries()) {
            //System.out.println("Adding header to EgressRequest : " + entry.getKey() + " -> " + entry.getValue());
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }
        clientReq = clientReq.withContentSource(nettyReq.getContent());
        return new EgressRequest<>(clientReq, ingressReq.getHttpServerRequest(), ingressReq.getNettyChannelContext(), requestState);
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

    public ChannelHandlerContext getNettyChannelContext() {
        return channelHandlerContext;
    }
}
