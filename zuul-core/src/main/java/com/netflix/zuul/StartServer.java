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
import io.reactivex.netty.protocol.http.client.HttpClient;
import rx.Observable;

public class StartServer {
    static final int DEFAULT_PORT = 8090;

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
