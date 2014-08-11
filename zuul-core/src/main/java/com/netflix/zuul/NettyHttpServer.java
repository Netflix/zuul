/**
 * Copyright 2014 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;

import java.util.Map;

public class NettyHttpServer {
    static final int DEFAULT_PORT = 8090;

    private final int port;
    private final FilterProcessor filterProcessor;

    public NettyHttpServer(int port, FilterProcessor filterProcessor) {
        this.port = port;
        this.filterProcessor = filterProcessor;
    }

    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.newHttpServerBuilder(port,
                (HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) -> {
                    final IngressRequest ingressReq = IngressRequest.from(request);
                    return filterProcessor.applyAllFilters(ingressReq).
                            doOnNext(egressResp ->  System.out.println("onNext Egress Resp : " + egressResp)).
                            doOnError(ex -> System.out.println("onError Egress Resp : " + ex)).
                            doOnCompleted(() -> System.out.println("onCompleted Egress Resp")).
                            cast(Void.class);
                }).pipelineConfigurator(PipelineConfigurators.<ByteBuf, ByteBuf>httpServerConfigurator()).build();

        System.out.println("Started Zuul Netty HTTP Server!!");
        return server;
    }

    public void printRequestHeader(HttpServerRequest<ByteBuf> request) {
        System.out.println("New request received");
        System.out.println(request.getHttpMethod() + " " + request.getUri() + ' ' + request.getHttpVersion());
        for (Map.Entry<String, String> header : request.getHeaders().entries()) {
            System.out.println(header.getKey() + ": " + header.getValue());
        }
    }

    public static void main(final String[] args) {
        FilterStore filterStore = new InMemoryFilterStore();
        FilterProcessor filterProcessor = new FilterProcessor(filterStore);
        NettyHttpServer server = new NettyHttpServer(DEFAULT_PORT, filterProcessor);

        server.createServer().startAndWait();
    }
}
