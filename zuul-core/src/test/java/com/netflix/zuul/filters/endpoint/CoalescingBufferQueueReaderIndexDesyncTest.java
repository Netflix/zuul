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

import io.netty.buffer.ByteBuf;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Replaying a chunk to a retried origin with a shared reader index (retain) can drain the buffer out from
 * under a CoalescingBufferQueue, leaving it empty. As of netty 4.2.16 the queue reconciles its readable byte
 * count back to zero once it drains empty (netty/netty#16946) instead of reporting bytes it can never produce,
 * so it no longer desyncs. retainedDuplicate gives each attempt its own reader index over the shared memory,
 * so it drains cleanly and actually produces the bytes.
 */
class CoalescingBufferQueueReaderIndexDesyncTest {

    private static final byte[] BODY = "Hello There".getBytes(StandardCharsets.UTF_8);

    @Test
    void retainSharesReaderIndexAndNettyReconcilesDrainedQueueToZero() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf body = channel.alloc().buffer().writeBytes(BODY);
        int bodyLength = body.readableBytes();

        CoalescingBufferQueue slowAttempt = new CoalescingBufferQueue(channel);
        slowAttempt.add(body.retain());
        assertThat(slowAttempt.readableBytes()).isEqualTo(bodyLength);

        // a retry drains the same shared instance, advancing the reader index the slow attempt's queue shares
        body.skipBytes(body.readableBytes());

        ByteBuf produced = slowAttempt.remove(channel.alloc(), bodyLength, channel.newPromise());

        // netty 4.2.16 reconciles readableBytes to 0 once the queue drains empty instead of reporting phantom bytes
        assertThat(produced.readableBytes()).isZero();
        assertThat(slowAttempt.isEmpty()).isTrue();
        assertThat(slowAttempt.readableBytes()).isZero();

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
}
