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
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

import java.util.Map;

public class EgressRequest {
    private HttpClientRequest<ByteBuf> nettyRequest;

    private EgressRequest(HttpClientRequest<ByteBuf> nettyRequest) {
        this.nettyRequest = nettyRequest;
    }

    public static EgressRequest copiedFrom(IngressRequest ingressReq) {
        HttpServerRequest<ByteBuf> nettyReq = ingressReq.getNettyRequest();
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(nettyReq.getHttpMethod(), nettyReq.getUri());
        for (Map.Entry<String, String> entry: nettyReq.getHeaders().entries()) {
            //System.out.println("Adding header to EgressRequest : " + entry.getKey() + " -> " + entry.getValue());
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }
        clientReq = clientReq.withContentSource(nettyReq.getContent());
        return new EgressRequest(clientReq);
    }

    public void addHeader(String name, String value) {
        nettyRequest = nettyRequest.withHeader(name, value);
    }

    public String getUri() {
        return nettyRequest.getUri();
    }

    public HttpClientRequest<ByteBuf> getUnderlyingNettyReq() {
        return nettyRequest;
    }
}
