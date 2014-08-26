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

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EgressRequest<T> {
    private HttpClientRequest<ByteBuf> nettyClientRequest;
    private final HttpServerRequest<ByteBuf> nettyServerRequest;
    private final ChannelHandlerContext channelHandlerContext;
    private final T state;
    
    private EgressRequest(HttpClientRequest<ByteBuf> nettyClientRequest, HttpServerRequest<ByteBuf> nettyServerRequest, ChannelHandlerContext channelHandlerContext, T state) {
        this.nettyClientRequest = nettyClientRequest;
        this.nettyServerRequest = nettyServerRequest;
        this.channelHandlerContext = channelHandlerContext;
        this.state = state;
    }

    public static <T> EgressRequest<T> copiedFrom(IngressRequest ingressReq, T requestState) {
        HttpServerRequest<ByteBuf> nettyReq = ingressReq.getNettyRequest();
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(nettyReq.getHttpMethod(), nettyReq.getUri());
        for (Map.Entry<String, String> entry: nettyReq.getHeaders().entries()) {
            //System.out.println("Adding header to EgressRequest : " + entry.getKey() + " -> " + entry.getValue());
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }
        clientReq = clientReq.withContentSource(nettyReq.getContent());
        return new EgressRequest<>(clientReq, ingressReq.getNettyRequest(), ingressReq.getNettyChannelContext(), requestState);
    }

    public void addHeader(String name, String value) {
        nettyClientRequest = nettyClientRequest.withHeader(name, value);
    }

    public String getHeaderValue(String name) {
        return nettyClientRequest.getHeaders().get(name);
    }

    public String getUri() {
        return nettyClientRequest.getUri();
    }

    public List<String> getParameter(String name) {
        Map<String, List<String>> params = nettyServerRequest.getQueryParameters();
        return params == null ? new ArrayList<>() : params.get(name);
    }

    public String getIpAddress() {
        SocketAddress remoteAddr = channelHandlerContext.channel().remoteAddress();
        return remoteAddr == null ? null : remoteAddr.toString();
    }

    public HttpClientRequest<ByteBuf> getUnderlyingNettyReq() {
        return nettyClientRequest;
    }
    
    public T get() {
        return state;
    }

    public ChannelHandlerContext getNettyChannelContext() {
        return channelHandlerContext;
    }
}
