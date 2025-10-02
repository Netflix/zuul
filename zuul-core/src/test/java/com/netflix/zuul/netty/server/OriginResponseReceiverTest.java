/*
 * Copyright 2025 Netflix, Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OriginResponseReceiverTest {

    @Mock
    private ProxyEndpoint proxyEndpoint;

    private OriginResponseReceiver receiver;
    private ChannelHandlerContext ctx;
    private EmbeddedChannel channel;
    private HttpContent chunk;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String msg) {}
        });

        ctx = channel.pipeline().firstContext();
        receiver = new OriginResponseReceiver(proxyEndpoint);

        // allocate a chunk for testing
        chunk = new DefaultHttpContent(Unpooled.wrappedBuffer("yo".getBytes(UTF_8)));
    }

    @Test
    void channelReadWithManualReadTriggersRead() throws Exception {
        // when triggerRead=true (default), should manually trigger read after processing
        receiver.channelReadInternal(ctx, chunk, true);

        verify(proxyEndpoint, times(1)).invokeNext(any(HttpContent.class));
        assertThat(chunk.refCnt()).isEqualTo(1);
    }

    @Test
    void channelReadWithoutManualReadDoesNotTriggerRead() throws Exception {
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.wrappedBuffer("test data".getBytes(UTF_8)));

        // when triggerRead=false, should NOT trigger read
        receiver.channelReadInternal(ctx, chunk2, false);

        verify(proxyEndpoint, times(1)).invokeNext(any(HttpContent.class));
        assertThat(chunk2.refCnt()).isEqualTo(1);

        chunk2.release();
    }

    @Test
    void unlinkFromClientRequestNullsEdgeProxyField() throws Exception {
        verifyProxyLink(receiver, false);

        // link is still there, so pipeline should be called & chunk retained
        receiver.channelReadInternal(ctx, chunk, true);
        verify(proxyEndpoint, times(1)).invokeNext(any(HttpContent.class));
        assertThat(chunk.refCnt()).isEqualTo(1);

        // when the client request is unlinked, field should be nulled
        receiver.unlinkFromClientRequest();
        verifyProxyLink(receiver, true);

        // data frames should be ignored now that the link is gone
        receiver.channelReadInternal(ctx, chunk, true);

        // pipeline should not be called again, as link is gone, so still 1 time
        verify(proxyEndpoint, times(1)).invokeNext(any(HttpContent.class));

        // verify chunk was released
        assertThat(chunk.refCnt()).isEqualTo(0);
    }

    @Test
    void unlinkPreventsStaleChunksFromBeingProcessed() throws Exception {

        // simulate retry scenario where connection is unlinked
        receiver.unlinkFromClientRequest();
        verifyProxyLink(receiver, true);

        // stale chunk arrives after unlink
        receiver.channelReadInternal(ctx, chunk, true);

        // should NOT call invokeNext - chunk should be released instead
        verify(proxyEndpoint, never()).invokeNext(any(HttpContent.class));

        // verify chunk was released (no memory leak)
        assertThat(chunk.refCnt()).isEqualTo(0);
    }

    @Test
    void httpResponseProcessedCorrectly() throws Exception {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        receiver.channelReadInternal(ctx, response, true);

        verify(proxyEndpoint, times(1)).responseFromOrigin(any(HttpResponse.class));
    }

    @Test
    void httpResponseReleasedWhenUnlinked() throws Exception {
        // use FullHttpResponse which has a refCnt
        HttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("body".getBytes(UTF_8)));

        assertThat(ReferenceCountUtil.refCnt(response)).isEqualTo(1);

        // unlink first
        receiver.unlinkFromClientRequest();

        // process response - should be released since unlinked
        receiver.channelReadInternal(ctx, response, true);

        verify(proxyEndpoint, never()).responseFromOrigin(any(HttpResponse.class));
        assertThat(ReferenceCountUtil.refCnt(response)).isEqualTo(0);
    }

    @Test
    void chunksReleasedWhenUnlinked() throws Exception {
        receiver.unlinkFromClientRequest();

        receiver.channelReadInternal(ctx, chunk, true);

        verify(proxyEndpoint, never()).invokeNext(any(HttpContent.class));
        assertThat(chunk.refCnt()).isEqualTo(0);
    }

    private void verifyProxyLink(OriginResponseReceiver receiver, boolean expectedNull) throws Exception {
        Field proxyField = OriginResponseReceiver.class.getDeclaredField("edgeProxy");
        proxyField.setAccessible(true);

        if (expectedNull) {
            assertThat(proxyField.get(receiver)).isNull();
        } else {
            assertThat(proxyField.get(receiver)).isNotNull();
        }
    }
}
