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
package com.netflix.zuul.karyon.filter.example.route;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.zuul.filter.RouteFilterIO;
import com.netflix.zuul.lifecycle.EgressRequest;
import com.netflix.zuul.lifecycle.IngressResponse;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientPipelineConfigurator;
import rx.Observable;

public class ExampleRouteFilter<T> extends RouteFilterIO<T> {

    private final NettyHttpClient<ByteBuf, ByteBuf> originClient;

    public ExampleRouteFilter() {
        IClientConfig config = IClientConfig.Builder.newBuilder().withDefaultValues()
                                                    .withDeploymentContextBasedVipAddresses("api-test.netflix.net:7001").build()
                                                    .set(IClientConfigKey.Keys.NIWSServerListClassName,
                                                         DiscoveryEnabledNIWSServerList.class.getName());
        originClient = RibbonTransport.newHttpClient(new HttpClientPipelineConfigurator<>(), config);
    }

    @Override
    public Observable<IngressResponse<T>> routeToOrigin(EgressRequest<T> egressReq) {
        System.out.println(this + " route filter");
        return originClient.submit(egressReq.getHttpClientRequest()).map(httpResp ->
                IngressResponse.from(httpResp, egressReq.get()));
    }

    @Override
    public int getOrder() {
        return 1;
    }
}