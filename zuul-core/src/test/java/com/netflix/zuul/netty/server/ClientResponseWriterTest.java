/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.zuul.netty.server;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.netflix.netty.common.HttpLifecycleChannelHandler;
import com.netflix.zuul.BasicRequestCompleteHandler;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.message.util.HttpRequestBuilder;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientResponseWriterTest {

    @Test
    void exemptClientTimeoutResponseBeforeRequestRead() {
        ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler());
        EmbeddedChannel channel = new EmbeddedChannel();

        SessionContext context = new SessionContext();
        StatusCategoryUtils.setStatusCategory(context, ZuulStatusCategory.FAILURE_CLIENT_TIMEOUT);
        HttpRequestMessage request = new HttpRequestBuilder(context).withDefaults();
        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);

        assertThat(responseWriter.shouldAllowPreemptiveResponse(channel)).isTrue();
    }

    @Test
    void flagResponseBeforeRequestRead() {
        ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler());
        EmbeddedChannel channel = new EmbeddedChannel();

        SessionContext context = new SessionContext();
        StatusCategoryUtils.setStatusCategory(context, ZuulStatusCategory.FAILURE_LOCAL);
        HttpRequestMessage request = new HttpRequestBuilder(context).withDefaults();
        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);

        assertThat(responseWriter.shouldAllowPreemptiveResponse(channel)).isFalse();
    }

    @Test
    void allowExtensionForPremptingResponse() {

        ZuulStatusCategory customStatus = ZuulStatusCategory.SUCCESS_LOCAL_NO_ROUTE;
        ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler()) {
            @Override
            protected boolean shouldAllowPreemptiveResponse(Channel channel) {
                StatusCategory status =
                        StatusCategoryUtils.getStatusCategory(ClientRequestReceiver.getRequestFromChannel(channel));
                return status == customStatus;
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext();
        StatusCategoryUtils.setStatusCategory(context, customStatus);
        HttpRequestMessage request = new HttpRequestBuilder(context).withDefaults();
        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);

        assertThat(responseWriter.shouldAllowPreemptiveResponse(channel)).isTrue();
    }

    @Test
    public void clearReferenceOnComplete() {
        ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler());
        EmbeddedChannel channel = new EmbeddedChannel(responseWriter);

        AtomicReference<HttpResponse> nettyResp = new AtomicReference<>();
        channel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof HttpResponse response) {
                    nettyResp.set(response);
                }
                ReferenceCountUtil.safeRelease(msg);
            }
        });

        SessionContext ctx = new SessionContext();
        HttpRequestMessage request = new HttpRequestBuilder(ctx).build();
        request.storeInboundRequest();
        HttpResponseMessageImpl response = new HttpResponseMessageImpl(ctx, request, 200);
        response.setHeaders(new Headers());

        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);
        DefaultHttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        ctx.set(CommonContextKeys.NETTY_HTTP_REQUEST, nettyRequest);

        channel.pipeline().fireUserEventTriggered(new HttpLifecycleChannelHandler.StartEvent(nettyRequest));
        channel.writeInbound(response);

        HttpResponseMessage zuulResponse = responseWriter.getZuulResponse();
        assertNotNull(zuulResponse);
        assertNotNull(nettyResp.get());

        channel.pipeline()
                .fireUserEventTriggered(new HttpLifecycleChannelHandler.CompleteEvent(
                        HttpLifecycleChannelHandler.CompleteReason.SESSION_COMPLETE, null, nettyResp.get()));
        assertNull(responseWriter.getZuulResponse());
    }
}
