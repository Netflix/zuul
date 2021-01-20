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

import static com.google.common.truth.Truth.assertThat;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2ResetFrame;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Http2ContentLengthEnforcingHandlerTest {

    @Test
    public void failsOnMultipleContentLength() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.pipeline().addLast(new Http2ContentLengthEnforcingHandler());

        HttpRequest req = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 2);
        chan.writeInbound(req);

        Object out = chan.readOutbound();
        assertThat(out).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    public void failsOnMixedContentLengthAndChunked() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.pipeline().addLast(new Http2ContentLengthEnforcingHandler());

        HttpRequest req = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "identity, chunked");
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "fzip");
        chan.writeInbound(req);

        Object out = chan.readOutbound();
        assertThat(out).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    public void failsOnShortContentLength() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.pipeline().addLast(new Http2ContentLengthEnforcingHandler());

        DefaultHttpRequest req = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 1);
        chan.writeInbound(req);

        Object out = chan.readOutbound();
        assertThat(out).isNull();

        DefaultHttpContent content =
                new DefaultHttpContent(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "a"));
        chan.writeInbound(content);

        out = chan.readOutbound();
        assertThat(out).isNull();

        DefaultHttpContent content2 =
                new DefaultHttpContent(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "a"));
        chan.writeInbound(content2);

        out = chan.readOutbound();
        assertThat(out).isInstanceOf(Http2ResetFrame.class);
    }

    @Test
    public void failsOnShortContent() {
        EmbeddedChannel chan = new EmbeddedChannel();
        chan.pipeline().addLast(new Http2ContentLengthEnforcingHandler());

        DefaultHttpRequest req = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 2);
        chan.writeInbound(req);

        Object out = chan.readOutbound();
        assertThat(out).isNull();

        DefaultHttpContent content =
                new DefaultHttpContent(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "a"));
        chan.writeInbound(content);

        out = chan.readOutbound();
        assertThat(out).isNull();

        DefaultHttpContent content2 = new DefaultLastHttpContent();
        chan.writeInbound(content2);

        out = chan.readOutbound();
        assertThat(out).isInstanceOf(Http2ResetFrame.class);
    }

}
