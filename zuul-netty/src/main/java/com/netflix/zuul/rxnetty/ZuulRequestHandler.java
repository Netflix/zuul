/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.rxnetty;

import com.google.inject.Singleton;
import com.netflix.zuul.ZuulHttpProcessor;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

import javax.inject.Inject;

/**
 * An implementation of {@link RequestHandler} for zuul.
 *
 * @author Nitesh Kant
 * @author Mike Smith
 */
@Singleton
public class ZuulRequestHandler implements RequestHandler<ByteBuf, ByteBuf>
{
    private final ZuulHttpProcessor zuulProcessor;

    @Inject
    public ZuulRequestHandler(ZuulHttpProcessor zuulProcessor) {
        this.zuulProcessor = zuulProcessor;
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> nettyRequest, HttpServerResponse<ByteBuf> nettyResponse)
    {
        return zuulProcessor.process(nettyRequest, nettyResponse);
    }
}
