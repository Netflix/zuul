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
package com.netflix.zuul.groovy.filter;

import com.netflix.zuul.EgressRequest;
import com.netflix.zuul.IngressResponse;
import com.netflix.zuul.RouteFilter;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import rx.Observable;

public class ExampleRouteFilter<T> extends RouteFilter<T> {

    @Override
    public Observable<IngressResponse> apply(EgressRequest<T> egressReq) {
        println("route filter " + this)
        HttpClient<ByteBuf, ByteBuf> httpClient = RxNetty.createHttpClient("api.test.netflix.com", 80)
        httpClient.submit(egressReq.getUnderlyingNettyReq()).map({IngressResponse.from(it) })
    }

    @Override
    public Observable<Boolean> shouldFilter(EgressRequest<T> input) {
        Observable.just(true)
    }
}