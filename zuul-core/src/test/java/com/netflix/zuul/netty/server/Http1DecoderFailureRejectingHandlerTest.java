/*
 * Copyright 2026 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.netty.common.throttle.RejectionUtils;
import com.netflix.netty.common.throttle.RequestRejectedEvent;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http1DecoderFailureRejectingHandlerTest {

    private EmbeddedChannel channel;
    private List<RequestRejectedEvent> capturedEvents;

    @BeforeEach
    void setup() {
        capturedEvents = new ArrayList<>();
        channel = new EmbeddedChannel(
                new Http1DecoderFailureRejectingHandler(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof RequestRejectedEvent rejectedEvent) {
                            capturedEvents.add(rejectedEvent);
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                });
    }

    @Test
    void requestWithDecoderFailureIsRejected() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.setDecoderResult(DecoderResult.failure(new IllegalArgumentException("bad header")));

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound())
                .as("request must not propagate past the handler")
                .isNull();
        assertThat(channel.<Object>readOutbound())
                .as("no response is written - parser failed mid-message and body bounds are untrustworthy")
                .isNull();
        assertThat(channel.isOpen())
                .as("connection must be closed so residual bytes are not re-parsed as a new request")
                .isFalse();

        assertThat(capturedEvents).hasSize(1);
        RequestRejectedEvent event = capturedEvents.getFirst();
        assertThat(event.httpStatus()).isEqualTo(RejectionUtils.REJECT_CLOSING_STATUS);
        assertThat(event.nfStatus()).isEqualTo(ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST);
        assertThat(event.reason()).isEqualTo("http1_decoder_failure");
        assertThat(event.request()).isSameAs(req);
    }

    @Test
    void requestWithoutDecoderFailurePassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isSameAs(req);
        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isTrue();
        assertThat(capturedEvents).isEmpty();
    }
}
