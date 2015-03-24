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
package com.netflix.zuul.filter.example.route;

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.filter.RouteFilterIO;
import com.netflix.zuul.lifecycle.EgressRequest;
import com.netflix.zuul.lifecycle.HttpResponseMessage;
import com.netflix.zuul.lifecycle.IngressResponse;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientPipelineConfigurator;
import rx.Observable;

public class ExampleRouteFilter<T> extends RouteFilterIO<T> {

    private final HttpClient<ByteBuf, ByteBuf> originClient;

    public ExampleRouteFilter() {
        originClient = RxNetty.<ByteBuf, ByteBuf>newHttpClientBuilder("api.test.netflix.com", 80)
                              .pipelineConfigurator(new HttpClientPipelineConfigurator<>())
                              .build();
    }

    @Override
    public Observable<IngressResponse<T>> routeToOrigin(EgressRequest<T> egressReq) {
        return originClient.submit(egressReq.getHttpClientRequest())
                           .map(httpResp -> {
                               // TODO - we're mid refactoring to use RequestContext, so have to additionally
                               // set the status on context.response here.
                               RequestContext ctx = (RequestContext) egressReq.get();
                               ((HttpResponseMessage) ctx.getResponse()).setStatus(httpResp.getStatus().code());

                               // Convert the client response to an IngressResponse.
                               return IngressResponse.from(httpResp, egressReq.get());
                           });
    }

    @Override
    public int getOrder() {
        return 1;
    }
}