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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.HttpResponseHeaders;
import rx.Observable;

public class IngressResponse {
    private final HttpResponseHeaders headers;
    private final HttpResponseStatus status;
    private final ByteBuf byteBuf;

    protected IngressResponse(HttpResponseHeaders headers, HttpResponseStatus status, ByteBuf byteBuf) {
        this.headers = headers;
        this.status = status;
        this.byteBuf = byteBuf;
    }

    public HttpResponseHeaders getHeaders() {
        return this.headers;
    }

    public HttpResponseStatus getStatus() {
        return this.status;
    }

    public ByteBuf getByteBuf() {
        return this.byteBuf;
    }

    public static Observable<IngressResponse> from(HttpClientResponse<ByteBuf> nettyResponse) {
        System.out.println("Received response : " + nettyResponse + " : " + nettyResponse.getStatus());
        int contentLength = getContentLength(nettyResponse);
        if (contentLength == 0) {
            return Observable.just(new IngressResponse(nettyResponse.getHeaders(), nettyResponse.getStatus(), null));
        } else {
            return nettyResponse.getContent().map(byteBuf -> {
                //Must retain in order to maintain proper reference count.
                //See https://github.com/Netflix/RxNetty/issues/203 for the rationale.
                byteBuf.retain();
                return new IngressResponse(nettyResponse.getHeaders(), nettyResponse.getStatus(), byteBuf);
            });
        }
    }

    private static int getContentLength(HttpClientResponse<ByteBuf> nettyResp) {
        HttpResponseHeaders headers = nettyResp.getHeaders();
        if (headers.contains("Content-Length")) {
            return headers.getIntHeader("Content-Length");
        } else {
            return 0;
        }
    }

    public boolean containsContent() {
        return byteBuf != null;
    }
}
