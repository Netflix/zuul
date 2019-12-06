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

package com.netflix.zuul.netty.common.proxyprotocol;

import com.google.common.net.InetAddresses;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.proxyprotocol.ElbProxyProtocolChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import java.net.InetSocketAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(JUnit4.class)
public class ElbProxyProtocolChannelHandlerTest {

    @Test
    public void noProxy() {
        ElbProxyProtocolChannelHandler handler = new ElbProxyProtocolChannelHandler(/* withProxyProtocol= */ false);
        EmbeddedChannel channel = new EmbeddedChannel();
        handler.addProxyProtocol(channel.pipeline());
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertEquals(dropped, buf);

        // TODO(carl-mastrangelo): the handler should remove itself, but it currently doesn't.
        assertNotNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertNull(channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_VERSION).get());
        assertNull(channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_PORT).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_PORT).get());
        assertNull(channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());

    }

    @Test
    public void negotiateProxy_ppv1_ipv4() {
        ElbProxyProtocolChannelHandler handler = new ElbProxyProtocolChannelHandler(/* withProxyProtocol= */ true);
        EmbeddedChannel channel = new EmbeddedChannel();
        handler.addProxyProtocol(channel.pipeline());
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertNull(dropped);

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertEquals(
                HAProxyProtocolVersion.V1, channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_VERSION).get());
        // TODO(carl-mastrangelo): this check is in place, but it should be removed.  The message is not properly GC'd
        // in later versions of netty.
        assertNotNull(channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertEquals("124.123.111.111", channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("124.123.111.111"), 443),
                channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertEquals(Integer.valueOf(443), channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_PORT).get());
        assertEquals("192.168.0.1", channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertEquals(Integer.valueOf(10008), channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_PORT).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("192.168.0.1"), 10008),
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }

    @Test
    public void negotiateProxy_ppv1_ipv6() {
        ElbProxyProtocolChannelHandler handler = new ElbProxyProtocolChannelHandler(/* withProxyProtocol= */ true);
        EmbeddedChannel channel = new EmbeddedChannel();
        handler.addProxyProtocol(channel.pipeline());
        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP6 ::1 ::2 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertNull(dropped);

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertEquals(
                HAProxyProtocolVersion.V1, channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_VERSION).get());
        // TODO(carl-mastrangelo): this check is in place, but it should be removed.  The message is not properly GC'd
        // in later versions of netty.
        assertNotNull(channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertEquals("::2", channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("::2"), 443),
                channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertEquals(Integer.valueOf(443), channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_PORT).get());
        assertEquals("::1", channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertEquals(Integer.valueOf(10008), channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_PORT).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("::1"), 10008),
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }

    @Test
    public void negotiateProxy_ppv2_ipv4() {
        ElbProxyProtocolChannelHandler handler = new ElbProxyProtocolChannelHandler(/* withProxyProtocol= */ true);
        EmbeddedChannel channel = new EmbeddedChannel();
        handler.addProxyProtocol(channel.pipeline());

        ByteBuf buf = Unpooled.wrappedBuffer(
                new byte[]{0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, 0x21, 0x11, 0x00,
                        0x0C, (byte) 0xC0, (byte) 0xA8, 0x00, 0x01, 0x7C, 0x7B, 0x6F, 0x6F, 0x27, 0x18,  0x01,
                        (byte) 0xbb});
        channel.writeInbound(buf);

        Object dropped = channel.readInbound();
        assertNull(dropped);

        // The handler should remove itself.
        assertNull(channel.pipeline().context(ElbProxyProtocolChannelHandler.NAME));
        assertEquals(
                HAProxyProtocolVersion.V2, channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_VERSION).get());
        // TODO(carl-mastrangelo): this check is in place, but it should be removed.  The message is not properly GC'd
        // in later versions of netty.
        assertNotNull(channel.attr(ElbProxyProtocolChannelHandler.ATTR_HAPROXY_MESSAGE).get());
        assertEquals("124.123.111.111", channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("124.123.111.111"), 443),
                channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).get());
        assertEquals(Integer.valueOf(443), channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_PORT).get());
        assertEquals("192.168.0.1", channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).get());
        assertEquals(Integer.valueOf(10008), channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_PORT).get());
        assertEquals(
                new InetSocketAddress(InetAddresses.forString("192.168.0.1"), 10008),
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get());
    }
}
