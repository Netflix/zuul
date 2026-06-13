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

package com.netflix.zuul.filters.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.EventExecutor;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Replaying a chunk to a retried origin with a shared reader index can leave the real CoalescingBufferQueue
 * reporting readable bytes it can never produce, and a frame stuck in that state spins the real
 * DefaultHttp2RemoteFlowController emitting empty DATA frames forever. retainedDuplicate gives each attempt
 * its own reader index over the shared memory, so it drains cleanly.
 */
class CoalescingBufferQueueReaderIndexDesyncTest {

    private static final byte[] BODY = "Hello There".getBytes(StandardCharsets.UTF_8);

    @Test
    void retainSharesReaderIndexAndDesyncsRealCoalescingBufferQueue() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf body = channel.alloc().buffer().writeBytes(BODY);
        int bodyLength = body.readableBytes();

        CoalescingBufferQueue slowAttempt = new CoalescingBufferQueue(channel);
        slowAttempt.add(body.retain());
        assertThat(slowAttempt.readableBytes()).isEqualTo(bodyLength);

        // a retry drains the same shared instance, advancing the reader index the slow attempt's queue shares
        body.skipBytes(body.readableBytes());

        ByteBuf produced = slowAttempt.remove(channel.alloc(), bodyLength, channel.newPromise());

        assertThat(produced.readableBytes()).isZero();
        assertThat(slowAttempt.isEmpty()).isTrue();
        assertThat(slowAttempt.readableBytes()).isEqualTo(bodyLength);

        produced.release();
        body.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void retainedDuplicateGivesIndependentReaderIndexAndDrainsCleanly() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf body = channel.alloc().buffer().writeBytes(BODY);
        int bodyLength = body.readableBytes();

        CoalescingBufferQueue slowAttempt = new CoalescingBufferQueue(channel);
        slowAttempt.add(body.retainedDuplicate());
        assertThat(slowAttempt.readableBytes()).isEqualTo(bodyLength);

        body.skipBytes(body.readableBytes());

        ByteBuf produced = slowAttempt.remove(channel.alloc(), bodyLength, channel.newPromise());

        assertThat(produced.toString(StandardCharsets.UTF_8)).isEqualTo("Hello There");
        assertThat(slowAttempt.isEmpty()).isTrue();
        assertThat(slowAttempt.readableBytes()).isZero();

        produced.release();
        body.release();
        channel.finishAndReleaseAll();
    }

    // TODO(netty #16946): this asserts the *unbounded* spin present in our netty (4.2.15). Once the netty fix
    // lands writeAllocatedBytes bails after a bounded number of no-progress passes, so flip this to assert
    // the frame is errored after a small emitted count.
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void desyncedFrameSpinsRealFlowControllerEmittingUnboundedEmptyFrames() throws Http2Exception {
        long emptyFrameCap = 200_000L;

        DefaultHttp2Connection connection = new DefaultHttp2Connection(false);
        DefaultHttp2RemoteFlowController controller = new DefaultHttp2RemoteFlowController(connection);
        connection.remote().flowController(controller);
        Http2Stream stream = connection.local().createStream(1, false);

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        ChannelConfig config = mock(ChannelConfig.class);
        EventExecutor executor = mock(EventExecutor.class);
        when(channel.isWritable()).thenReturn(true);
        when(channel.bytesBeforeUnwritable()).thenReturn(Long.MAX_VALUE);
        when(channel.config()).thenReturn(config);
        when(executor.inEventLoop()).thenReturn(true);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(executor);
        controller.channelHandlerContext(ctx);

        StuckBodyFrame frame = new StuckBodyFrame(BODY.length, emptyFrameCap);
        controller.addFlowControlled(stream, frame);

        controller.writePendingBytes();

        assertThat(frame.emitted.get()).isGreaterThanOrEqualTo(emptyFrameCap);
        assertThat(frame.error).isInstanceOf(Http2Exception.class);
    }

    private static final class StuckBodyFrame implements Http2RemoteFlowController.FlowControlled {

        private final int size;
        private final long cap;
        private final AtomicLong emitted = new AtomicLong();
        private Throwable error;

        StuckBodyFrame(int size, long cap) {
            this.size = size;
            this.cap = cap;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void error(ChannelHandlerContext ctx, Throwable cause) {
            error = cause;
        }

        @Override
        public void writeComplete() {}

        @Override
        public void write(ChannelHandlerContext ctx, int allowedBytes) {
            // size() never shrinks so the frame is never removed; the cap throws to break netty's unbounded loop
            if (emitted.incrementAndGet() >= cap) {
                throw new SpinDetected();
            }
        }

        @Override
        public boolean merge(ChannelHandlerContext ctx, Http2RemoteFlowController.FlowControlled next) {
            return false;
        }
    }

    private static final class SpinDetected extends RuntimeException {
        SpinDetected() {
            super("flow controller emitted the capped number of empty frames without making progress");
        }
    }
}
