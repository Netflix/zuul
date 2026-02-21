/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul.netty.server.http2;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2ResetFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http2ContentLengthEnforcingHandlerTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel(new Http2ContentLengthEnforcingHandler());
    }

    @Test
    void validRequestPassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, 3);
        channel.writeInbound(req);

        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.<Object>readInbound()).isSameAs(req);
    }

    @Test
    void requestWithNoContentLengthPassesThrough() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        channel.writeInbound(req);

        assertThat(channel.<Object>readOutbound()).isNull();
        assertThat(channel.<Object>readInbound()).isSameAs(req);
    }

    @Test
    void rejectsMultipleContentLengthHeaders() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 2);
        channel.writeInbound(req);

        assertThat((Object) channel.readOutbound()).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    void failsOnNonNumericContentLength() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.pipeline().addLast(new Http2ContentLengthEnforcingHandler());

        ByteBuf content = Unpooled.buffer(8);
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, "not_a_number");

        chan.writeInbound(req);

        Object out = chan.readOutbound();
        assertThat(out).isInstanceOf(Http2ResetFrame.class);
        assertThat(content.refCnt()).isEqualTo(0);
    }

    @Test
    void rejectsNegativeContentLength() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, -5);
        channel.writeInbound(req);

        assertThat((Object) channel.readOutbound()).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    void rejectsMixedContentLengthAndChunked() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "identity, chunked");
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "fzip");
        channel.writeInbound(req);

        assertThat((Object) channel.readOutbound()).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    void rejectsContentExceedingDeclaredLength() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        channel.writeInbound(req);
        assertThat(channel.<Object>readOutbound()).isNull();

        DefaultHttpContent content =
                new DefaultHttpContent(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "a"));
        channel.writeInbound(content);
        assertThat(channel.<Object>readOutbound()).isNull();

        DefaultHttpContent content2 =
                new DefaultHttpContent(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "a"));
        channel.writeInbound(content2);
        assertThat((Object) channel.readOutbound()).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    void rejectsContentShorterThanDeclaredLength() {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 2);
        channel.writeInbound(req);
        assertThat(channel.<Object>readOutbound()).isNull();

        DefaultHttpContent content =
                new DefaultHttpContent(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "a"));
        channel.writeInbound(content);
        assertThat(channel.<Object>readOutbound()).isNull();

        channel.writeInbound(new DefaultLastHttpContent());
        assertThat((Object) channel.readOutbound()).isInstanceOf(Http2ResetFrame.class);
    }
}
