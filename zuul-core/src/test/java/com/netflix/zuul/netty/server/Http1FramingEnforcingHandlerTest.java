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
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http1FramingEnforcingHandlerTest {

    private EmbeddedChannel channel;
    private List<RequestRejectedEvent> capturedEvents;

    @BeforeEach
    void setup() {
        capturedEvents = new ArrayList<>();
        channel = new EmbeddedChannel(
                new Http1FramingEnforcingHandler(),
                // capture user events fired up the pipeline so we can assert RequestRejectedEvent was fired
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
    void validRequestWithContentLengthPassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, 3);

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isSameAs(req);
        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isTrue();
        assertThat(capturedEvents).isEmpty();
    }

    @Test
    void validRequestWithTransferEncodingChunkedPassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isSameAs(req);
        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void requestWithNoFramingHeadersPassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isSameAs(req);
        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void teAndClRequestIsRejectedByForceClose() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/poc");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, 4);
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound())
                .as("request must not propagate past the handler")
                .isNull();
        assertThat(channel.<Object>readOutbound())
                .as("no response is written - framing is too ambiguous to safely respond to")
                .isNull();
        assertThat(channel.isOpen())
                .as("connection must be closed so residual bytes are not re-parsed as a new request")
                .isFalse();

        assertThat(capturedEvents).hasSize(1);
        RequestRejectedEvent event = capturedEvents.getFirst();
        assertThat(event.httpStatus()).isEqualTo(RejectionUtils.REJECT_CLOSING_STATUS);
        assertThat(event.nfStatus()).isEqualTo(ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST);
        assertThat(event.reason()).isEqualTo("http1_framing_violation");
        assertThat(event.request()).isSameAs(req);
    }

    @Test
    void http10RequestWithTransferEncodingIsRejected() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isNull();
        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isFalse();
        assertThat(capturedEvents.getFirst().reason()).isEqualTo("http1_framing_violation");
    }

    @Test
    void duplicateContentLengthIsRejected() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 2);

        channel.writeInbound(req);

        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isFalse();
        assertThat(capturedEvents.getFirst().reason()).isEqualTo("http1_framing_violation");
    }

    @Test
    void nonNumericContentLengthIsRejected() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, "not_a_number");

        channel.writeInbound(req);

        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isFalse();
        assertThat(capturedEvents.getFirst().reason()).isEqualTo("http1_framing_violation");
    }

    @Test
    void negativeContentLengthIsRejected() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, -1);

        channel.writeInbound(req);

        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isFalse();
        assertThat(capturedEvents.getFirst().reason()).isEqualTo("http1_framing_violation");
    }

    @Test
    void transferEncodingWithoutChunkedFinalIsRejected() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "gzip");

        channel.writeInbound(req);

        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isFalse();
        assertThat(capturedEvents.getFirst().reason()).isEqualTo("http1_framing_violation");
    }

    @Test
    void transferEncodingChunkedAsLastCodingPassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "gzip, chunked");

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isSameAs(req);
        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void multipleTransferEncodingHeadersWithChunkedAsFinalPasses() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "gzip");
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");

        channel.writeInbound(req);

        assertThat(channel.<Object>readInbound()).isSameAs(req);
        assertThat(channel.<Object>readOutbound()).isNull();
    }

    @Test
    void nonHttpRequestMessagesArePassedThrough() {
        channel.writeInbound("not-an-http-request");

        assertThat(channel.<Object>readInbound()).isEqualTo("not-an-http-request");
    }
}
