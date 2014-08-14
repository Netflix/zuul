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
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;
import rx.functions.Func1;

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
                    return filterProcessor.applyAllFilters(ingressReq, response).
                            flatMap(EgressResponse::getContent).
                            map(new Func1<ByteBuf, Void>() {
                                @Override
                                public Void call(ByteBuf byteBuf) {
                                    byteBuf.retain();
                                    response.write(byteBuf);
                                    return null;
                                }
                            }).
                            doOnCompleted(response::close);
                }).pipelineConfigurator(PipelineConfigurators.<ByteBuf, ByteBuf>httpServerConfigurator()).build();

        System.out.println("Started Zuul Netty HTTP Server!!");
        return server;
    }

    public static void main(final String[] args) {
        FilterStore filterStore = new InMemoryFilterStore();
        FilterProcessor filterProcessor = new FilterProcessor(filterStore);
        NettyHttpServer server = new NettyHttpServer(DEFAULT_PORT, filterProcessor);

        //This is for testing only and will get removed once we have user-defined scripts hooked in
        addJavaFilters(filterStore);

        server.createServer().startAndWait();
    }

    private static void addJavaFilters(FilterStore filterStore) {
        PreFilter preFilter = new PreFilter() {
            @Override
            public Observable<EgressRequest> apply(IngressRequest ingressReq, EgressRequest egressReq) {
                System.out.println("PreFilter doing a no-op");
                return Observable.just(egressReq);
            }

            @Override
            public int getOrder() {
                return 1;
            }

            @Override
            public Observable<Boolean> shouldFilter(IngressRequest ingressReq) {
                return Observable.just(true);
            }
        };

        filterStore.addFilter(preFilter);

        RouteFilter routeFilter = new RouteFilter() {
            @Override
            public Observable<IngressResponse> apply(EgressRequest egressReq) {
                HttpClient<ByteBuf, ByteBuf> httpClient = RxNetty.createHttpClient("api.test.netflix.com", 80);
                return httpClient.submit(egressReq.getUnderlyingNettyReq()).map(IngressResponse::from);
            }

            @Override
            public Observable<Boolean> shouldFilter(EgressRequest ingressReq) {
                return Observable.just(true);
            }
        };

        filterStore.addFilter(routeFilter);

        PostFilter postFilter = new PostFilter() {
            @Override
            public Observable<EgressResponse> apply(IngressResponse ingressResp, EgressResponse egressResp) {
                System.out.println("PostFilter doing a no-op");
                return Observable.just(egressResp);
            }

            @Override
            public int getOrder() {
                return 1;
            }

            @Override
            public Observable<Boolean> shouldFilter(IngressResponse ingressReq) {
                return Observable.just(true);
            }
        };

        filterStore.addFilter(postFilter);
    }
}
