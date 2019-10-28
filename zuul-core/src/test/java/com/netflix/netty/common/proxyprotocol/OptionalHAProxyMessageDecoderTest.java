/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.netty.common.proxyprotocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.ReferenceCounted;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link OptionalHAProxyMessageDecoder}.
 */
@RunWith(JUnit4.class)
public class OptionalHAProxyMessageDecoderTest {
    @Test
    public void detectsPpv1Message() {
        OptionalHAProxyMessageDecoder decoder = new OptionalHAProxyMessageDecoder();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(OptionalHAProxyMessageDecoder.NAME, decoder);

        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object msg = channel.readInbound();
        HAProxyMessage hapm = (HAProxyMessage) msg;

        // The handler should remove itself.
        assertNull(channel.pipeline().context(OptionalHAProxyMessageDecoder.NAME));

        // TODO(carl-mastrangelo): make this always happen once netty has been upgraded.
        if (hapm instanceof ReferenceCounted) {
            hapm.release();
        }
    }

    @Test
    @Ignore // TODO(carl-mastrangelo): reenable this.
    public void detectsSplitPpv1Message() {
        OptionalHAProxyMessageDecoder decoder = new OptionalHAProxyMessageDecoder();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(OptionalHAProxyMessageDecoder.NAME, decoder);

        ByteBuf buf1 = Unpooled.wrappedBuffer(
                "PROXY TCP4".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf1);
        ByteBuf buf2 = Unpooled.wrappedBuffer(
                "192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf2);

        Object msg = channel.readInbound();
        HAProxyMessage hapm = (HAProxyMessage) msg;

        // The handler should remove itself.
        assertNull(channel.pipeline().context(OptionalHAProxyMessageDecoder.NAME));

        // TODO(carl-mastrangelo): make this always happen once netty has been upgraded.
        if (hapm instanceof ReferenceCounted) {
            hapm.release();
        }
    }

    @Test
    public void extraDataForwarded() {
        OptionalHAProxyMessageDecoder decoder = new OptionalHAProxyMessageDecoder();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(OptionalHAProxyMessageDecoder.NAME, decoder);

        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\nPOTATO".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object msg = channel.readInbound();
        HAProxyMessage hapm = (HAProxyMessage) msg;

        // The handler should remove itself.
        assertNull(channel.pipeline().context(OptionalHAProxyMessageDecoder.NAME));

        // TODO(carl-mastrangelo): make this always happen once netty has been upgraded.
        if (hapm instanceof ReferenceCounted) {
            hapm.release();
        }

        Object msg2 = channel.readInbound();
        ByteBuf readBuf = (ByteBuf) msg2;
        assertEquals("POTATO", new String(ByteBufUtil.getBytes(readBuf), StandardCharsets.US_ASCII));
        readBuf.release();
    }

    @Test
    public void ignoresNonPpMessage() {
        OptionalHAProxyMessageDecoder decoder = new OptionalHAProxyMessageDecoder();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(OptionalHAProxyMessageDecoder.NAME, decoder);

        ByteBuf buf = Unpooled.wrappedBuffer(
                "BOGUS TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object msg = channel.readInbound();
        ByteBuf readBuf = (ByteBuf) msg;
        readBuf.release();

        // TODO(carl-mastrangelo): this is wrong, it should remove itself.   Change it.
        assertNotNull(channel.pipeline().context(OptionalHAProxyMessageDecoder.NAME));
    }
}
