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
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;

import java.util.Map;

public class EgressResponse {
    private final HttpServerResponse<ByteBuf> nettyResp;
    private final Observable<ByteBuf> content;

    private EgressResponse(HttpServerResponse<ByteBuf> nettyResp, Observable<ByteBuf> content) {
        this.nettyResp = nettyResp;
        this.content = content;
    }

    public static EgressResponse from(IngressResponse ingressResp, HttpServerResponse<ByteBuf> nettyResp) {
        nettyResp.setStatus(ingressResp.getStatus());

        for (Map.Entry<String, String> entry: ingressResp.getHeaders().entries()) {
            nettyResp.getHeaders().add(entry.getKey(), entry.getValue());
        }

        return new EgressResponse(nettyResp, ingressResp.getContent());
    }

    public Observable<ByteBuf> getContent() {
        return content;
    }

    public void addHeader(String name, String value) {
        nettyResp.getHeaders().addHeader(name, value);
    }
}
