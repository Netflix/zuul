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

import com.netflix.zuul.context.RequestContext;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

/**
 * TODO - completely replace with RequestContext?
 * Class that represents an incoming HTTP request and wraps an RxNetty {@link HttpServerRequest}
 */
public class IngressRequest {

    private final HttpServerRequest<ByteBuf> httpServerRequest;
    private final RequestContext context;

    private IngressRequest(HttpServerRequest<ByteBuf> httpServerRequest, RequestContext context) {
        this.httpServerRequest = httpServerRequest;
        this.context = context;
    }

    public static IngressRequest from(HttpServerRequest<ByteBuf> nettyRequest, RequestContext context) {
        return new IngressRequest(nettyRequest, context);
    }

    public HttpServerRequest<ByteBuf> getHttpServerRequest() {
        return httpServerRequest;
    }

    public RequestContext getRequestContext() {
        return context;
    }
}
