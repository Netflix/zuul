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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.google.common.net.InetAddresses;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ElbProxyProtocolChannelHandlerTest {

    @Mock
    private Registry registry;
    @Mock
    private Counter counter;

    @Before
    public void setup() {
        when(registry.counter("zuul.hapm.failure")).thenReturn(counter);
    }

    @Test
    public void noProxy() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, false));
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertEquals(dropped, buf);
        buf.release();

        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertNull(channel.pipeline().context("HAProxyMessageChannelHandler"));
        assertNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION).get());
        assertNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }

    @Test
    public void extraDataForwarded() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\nPOTATO".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object msg = channel.readInbound();
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));

        ByteBuf readBuf = (ByteBuf) msg;
        assertEquals("POTATO", new String(ByteBufUtil.getBytes(readBuf), StandardCharsets.US_ASCII));
        readBuf.release();
    }

    @Test
    public void passThrough_ProxyProtocolEnabled_nonProxyBytes() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        //Note that the bytes aren't prefixed by PROXY, as required by the spec
        ByteBuf buf = Unpooled.wrappedBuffer(
                "TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertEquals(dropped, buf);
        buf.release();

        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertNull(channel.pipeline().context("HAProxyMessageChannelHandler"));
        assertNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION).get());
        assertNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }

    @Test
    public void incrementCounterWhenPPEnabledButNonHAPMMessage() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        //Note that the bytes aren't prefixed by PROXY, as required by the spec
        ByteBuf buf = Unpooled.wrappedBuffer(
                "TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertEquals(dropped, buf);
        buf.release();

        verify(counter, times(1)).increment();
    }

    @Ignore
    @Test
    public void detectsSplitPpv1Message() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        ByteBuf buf1 = Unpooled.wrappedBuffer(
                "PROXY TCP4".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf1);
        ByteBuf buf2 = Unpooled.wrappedBuffer(
                "192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf2);

        Object msg = channel.readInbound();
        assertTrue(msg instanceof HAProxyMessage);
        buf1.release();
        buf2.release();
        ((HAProxyMessage) msg).release();

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.class));
    }

    @Test
    public void negotiateProxy_ppv1_ipv4() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertNull(dropped);

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertNull(channel.pipeline().context(HAProxyMessageChannelHandler.class));
        assertEquals(HAProxyProtocolVersion.V1, channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION).get());
        // TODO(carl-mastrangelo): this check is in place, but it should be removed.  The message is not properly GC'd
        // in later versions of netty.
        assertNotNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertEquals("124.123.111.111", channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertEquals(new InetSocketAddress(InetAddresses.forString("124.123.111.111"), 443),
                channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertEquals("192.168.0.1", channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertEquals(new InetSocketAddress(InetAddresses.forString("192.168.0.1"), 10008),
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }

    @Test
    public void negotiateProxy_ppv1_ipv6() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP6 ::1 ::2 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertNull(dropped);

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertEquals(
                HAProxyProtocolVersion.V1, channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION).get());
        // TODO(carl-mastrangelo): this check is in place, but it should be removed.  The message is not properly GC'd
        // in later versions of netty.
        assertNotNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertEquals("::2", channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("::2"), 443),
                channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertEquals("::1", channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("::1"), 10008),
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }

    @Test
    public void negotiateProxy_ppv2_ipv4() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(ElbProxyProtocolChannelHandler.NAME, new ElbProxyProtocolChannelHandler(registry, true));
        ByteBuf buf = Unpooled.wrappedBuffer(
                new byte[]{0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, 0x21, 0x11, 0x00,
                        0x0C, (byte) 0xC0, (byte) 0xA8, 0x00, 0x01, 0x7C, 0x7B, 0x6F, 0x6F, 0x27, 0x18, 0x01,
                        (byte) 0xbb});
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertNull(dropped);

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertEquals(
                HAProxyProtocolVersion.V2, channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION).get());
        // TODO(carl-mastrangelo): this check is in place, but it should be removed.  The message is not properly GC'd
        // in later versions of netty.
        assertNotNull(channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertEquals("124.123.111.111", channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("124.123.111.111"), 443),
                channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertEquals("192.168.0.1", channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("192.168.0.1"), 10008),
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }
}
