/*
 * Copyright 2022 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.filters.endpoint;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.spectator.api.Spectator;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.netty.NettyRequestAttemptFactory;
import com.netflix.zuul.netty.server.MethodBinding;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyEndpointTest {

    ProxyEndpoint proxyEndpoint;
    HttpRequestMessage request;

    @BeforeEach
    void setup() {
        ChannelHandlerContext chc = mock(ChannelHandlerContext.class);
        NettyRequestAttemptFactory attemptFactory = mock(NettyRequestAttemptFactory.class);

        request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", null,
                null,
                "192.168.0.2", "https", 7002, "localhost", new LocalAddress("777"), false);

        request.setBody("Hello There".getBytes());
        request.getContext().set(CommonContextKeys.ORIGIN_MANAGER, new BasicNettyOriginManager(Spectator.globalRegistry()));
        request.getContext().setRouteVIP("some-vip");
        request.getContext().put(CommonContextKeys.PASSPORT, CurrentPassport.create());
        proxyEndpoint = new ProxyEndpoint(request, chc, null, MethodBinding.NO_OP_BINDING, attemptFactory);
    }

    @Test
    void testRetryWillResetBodyReader() {

        assertEquals("Hello There", new String(request.getBody()));

        // move the body readerIndex to the end to mimic nettys behavior after writing to the origin channel
        request.getBodyContents().forEach((b) -> b.content().readerIndex(b.content().capacity()));

        HttpResponse response = mock(HttpResponse.class);
        when(response.status()).thenReturn(new HttpResponseStatus(503, "Retry"));

        InstanceInfo instanceInfo =
                InstanceInfo.Builder.newBuilder().setAppName("app").setHostName("localhost").setPort(443).build();
        DiscoveryResult discoveryResult = DiscoveryResult.from(instanceInfo, true);

        // when retrying a response, the request body reader should have it's indexes reset
        proxyEndpoint.handleOriginNonSuccessResponse(response, discoveryResult);
        assertEquals("Hello There", new String(request.getBody()));
    }
}