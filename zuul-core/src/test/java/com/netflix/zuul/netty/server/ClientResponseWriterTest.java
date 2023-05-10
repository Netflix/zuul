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
import com.netflix.zuul.BasicRequestCompleteHandler;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.util.HttpRequestBuilder;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientResponseWriterTest {

    @Test
    void exemptClientTimeoutResponseBeforeRequestRead() {
        final ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler());
        final EmbeddedChannel channel = new EmbeddedChannel();

        final SessionContext context = new SessionContext();
        context.put(CommonContextKeys.STATUS_CATGEORY, ZuulStatusCategory.FAILURE_CLIENT_TIMEOUT);
        final HttpRequestMessage request = new HttpRequestBuilder(context).withDefaults();
        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);

        assertThat(responseWriter.shouldAllowPreemptiveResponse(channel)).isTrue();
    }

    @Test
    void flagResponseBeforeRequestRead() {
        final ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler());
        final EmbeddedChannel channel = new EmbeddedChannel();

        final SessionContext context = new SessionContext();
        context.put(CommonContextKeys.STATUS_CATGEORY, ZuulStatusCategory.FAILURE_LOCAL);
        final HttpRequestMessage request = new HttpRequestBuilder(context).withDefaults();
        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);

        assertThat(responseWriter.shouldAllowPreemptiveResponse(channel)).isFalse();
    }

    @Test
    void allowExtensionForPremptingResponse() {

        final ZuulStatusCategory customStatus = ZuulStatusCategory.SUCCESS_LOCAL_NO_ROUTE;
        final ClientResponseWriter responseWriter = new ClientResponseWriter(new BasicRequestCompleteHandler()) {
            @Override
            protected boolean shouldAllowPreemptiveResponse(Channel channel) {
                StatusCategory status = StatusCategoryUtils.getStatusCategory(
                        ClientRequestReceiver.getRequestFromChannel(channel));
                return status == customStatus;
            }
        };

        final EmbeddedChannel channel = new EmbeddedChannel();
        final SessionContext context = new SessionContext();
        context.put(CommonContextKeys.STATUS_CATGEORY, customStatus);
        final HttpRequestMessage request = new HttpRequestBuilder(context).withDefaults();
        channel.attr(ClientRequestReceiver.ATTR_ZUUL_REQ).set(request);

        assertThat(responseWriter.shouldAllowPreemptiveResponse(channel)).isTrue();
    }
}
