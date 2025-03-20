/*
 * Copyright 2020 Netflix, Inc.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyTLV;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class HAProxyMessageChannelHandlerTest {

    @Test
    void setClientDestPortForHAPM() {
        EmbeddedChannel channel = new EmbeddedChannel();
        // This is normally done by Server.
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        // This is to emulate `ElbProxyProtocolChannelHandler`
        channel.pipeline()
                .addLast(HAProxyMessageDecoder.class.getSimpleName(), new HAProxyMessageDecoder())
                .addLast(HAProxyMessageChannelHandler.class.getSimpleName(), new HAProxyMessageChannelHandler());

        ByteBuf buf = Unpooled.wrappedBuffer(
                "PROXY TCP4 192.168.0.1 124.123.111.111 10008 443\r\n".getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);

        Object result = channel.readInbound();
        assertNull(result);

        InetSocketAddress destAddress = channel.attr(
                        SourceAddressChannelHandler.ATTR_PROXY_PROTOCOL_DESTINATION_ADDRESS)
                .get();

        InetSocketAddress srcAddress = (InetSocketAddress)
                channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).get();

        assertEquals("124.123.111.111", destAddress.getHostString());
        assertEquals(443, destAddress.getPort());

        assertEquals("192.168.0.1", srcAddress.getHostString());
        assertEquals(10008, srcAddress.getPort());

        Attrs attrs = channel.attr(Server.CONN_DIMENSIONS).get();
        Integer port = HAProxyMessageChannelHandler.HAPM_DEST_PORT.get(attrs);
        assertEquals(443, port.intValue());
        String sourceIpVersion = HAProxyMessageChannelHandler.HAPM_SRC_IP_VERSION.get(attrs);
        assertEquals("v4", sourceIpVersion);
        String destIpVersion = HAProxyMessageChannelHandler.HAPM_DEST_IP_VERSION.get(attrs);
        assertEquals("v4", destIpVersion);
    }

    @Test
    void v2parseCustomTLVs() {
        byte[] header = new byte[46];

        // Build \r\n\r\n\0\r\nQUIT\n
        header[0] = 0x0D; //
        header[1] = 0x0A; // -----
        header[2] = 0x0D; // -----
        header[3] = 0x0A; // -----
        header[4] = 0x00; // -----
        header[5] = 0x0D; // -----
        header[6] = 0x0A; // -----
        header[7] = 0x51; // -----
        header[8] = 0x55; // -----
        header[9] = 0x49; // -----
        header[10] = 0x54; // -----
        header[11] = 0x0A; // -----

        header[12] = 0x21; // v2 PROXY
        header[13] = 0x11; // TCP over IPv4

        header[14] = 0x00; // Addl. bytes
        header[15] = (byte) 0x1E; // -----

        header[16] = (byte) 0xc0; // Src Addr 192.168.0.1
        header[17] = (byte) 0xa8; // -----
        header[18] = 0x00; // -----
        header[19] = 0x01; // -----

        header[20] = (byte) 0x7c; // Dst Addr 124.123.111.111
        header[21] = (byte) 0x7b; // -----
        header[22] = (byte) 0x6f; // -----
        header[23] = (byte) 0x6f; // -----

        header[24] = (byte) 0x27; // Source Port 10006
        header[25] = 0x16; // -----

        header[26] = 0x01; // Destination Port 443
        header[27] = (byte) 0xbb; // -----

        header[28] = (byte) (byte) 0xe2; // custom TLV type per spec
        header[29] = (byte) 0x00; // Remaining bytes
        header[30] = (byte) 0x0F; // -----

        header[31] = (byte) 0x6e; // n
        header[32] = (byte) 0x66; // f
        header[33] = (byte) 0x6c; // l
        header[34] = (byte) 0x78; // x
        header[35] = (byte) 0x2e; // .
        header[36] = (byte) 0x63; // c
        header[37] = (byte) 0x75; // u
        header[38] = (byte) 0x73; // s
        header[39] = (byte) 0x74; // t
        header[40] = (byte) 0x6f; // o
        header[41] = (byte) 0x6d; // m
        header[42] = (byte) 0x2e; // .
        header[43] = (byte) 0x74; // t
        header[44] = (byte) 0x6c; // l
        header[45] = (byte) 0x76; // v

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(HAProxyMessageDecoder.class.getSimpleName(), new HAProxyMessageDecoder())
                .addLast(HAProxyMessageChannelHandler.class.getSimpleName(), new HAProxyMessageChannelHandler());

        channel.writeInbound(Unpooled.wrappedBuffer(header));

        Object result = channel.readInbound();
        assertNull(result);

        HAProxyMessage hapm =
                channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get();

        assertThat(hapm.sourceAddress()).isEqualTo("192.168.0.1");
        assertThat(hapm.destinationAddress()).isEqualTo("124.123.111.111");
        assertThat(hapm.sourcePort()).isEqualTo(10006);
        assertThat(hapm.destinationPort()).isEqualTo(443);

        List<HAProxyTLV> nflxTLV = channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_CUSTOM_TLVS)
                .get();
        Assert.assertEquals(nflxTLV.size(), 1);
        String payload = nflxTLV.get(0).content().toString(StandardCharsets.UTF_8);
        assertThat(payload).isEqualTo("nflx.custom.tlv");
    }

    @Test
    void validatev2TCPV4NoTLVs() {

        byte[] header = new byte[28];

        // Build \r\n\r\n\0\r\nQUIT\n
        header[0] = 0x0D; //
        header[1] = 0x0A; // -----
        header[2] = 0x0D; // -----
        header[3] = 0x0A; // -----
        header[4] = 0x00; // -----
        header[5] = 0x0D; // -----
        header[6] = 0x0A; // -----
        header[7] = 0x51; // -----
        header[8] = 0x55; // -----
        header[9] = 0x49; // -----
        header[10] = 0x54; // -----
        header[11] = 0x0A; // -----

        header[12] = 0x21; // v2 PROXY
        header[13] = 0x11; // TCP over IPv4

        header[14] = 0x00; // Addl. bytes
        header[15] = (byte) 0x0C; // -----

        header[16] = (byte) 0xc0; // Src Addr 192.168.0.1
        header[17] = (byte) 0xa8; // -----
        header[18] = 0x00; // -----
        header[19] = 0x01; // -----

        header[20] = (byte) 0x7c; // Dst Addr 124.123.111.111
        header[21] = (byte) 0x7b; // -----
        header[22] = (byte) 0x6f; // -----
        header[23] = (byte) 0x6f; // -----

        header[24] = (byte) 0x27; // Source Port 10006
        header[25] = 0x16; // -----

        header[26] = 0x01; // Destination Port 443
        header[27] = (byte) 0xbb; // -----

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(HAProxyMessageDecoder.class.getSimpleName(), new HAProxyMessageDecoder())
                .addLast(HAProxyMessageChannelHandler.class.getSimpleName(), new HAProxyMessageChannelHandler());

        channel.writeInbound(Unpooled.wrappedBuffer(header));

        Object result = channel.readInbound();
        assertNull(result);

        HAProxyMessage hapm =
                channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get();

        assertThat(hapm.sourceAddress()).isEqualTo("192.168.0.1");
        assertThat(hapm.destinationAddress()).isEqualTo("124.123.111.111");
        assertThat(hapm.sourcePort()).isEqualTo(10006);
        assertThat(hapm.destinationPort()).isEqualTo(443);

        List<HAProxyTLV> customTLV = channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_CUSTOM_TLVS)
                .get();
        assertThat(customTLV.isEmpty()).isEqualTo(true);
    }

    @Test
    void validateV2TCPV6NoTLVS() {
        byte[] header = new byte[52];

        // Build \r\n\r\n\0\r\nQUIT\n
        header[0] = 0x0D; //
        header[1] = 0x0A; // -----
        header[2] = 0x0D; // -----
        header[3] = 0x0A; // -----
        header[4] = 0x00; // -----
        header[5] = 0x0D; // -----
        header[6] = 0x0A; // -----
        header[7] = 0x51; // -----
        header[8] = 0x55; // -----
        header[9] = 0x49; // -----
        header[10] = 0x54; // -----
        header[11] = 0x0A; // -----

        header[12] = 0x21; // v2 PROXY
        header[13] = 0x21; // TCP over IPv6

        header[14] = 0x00; // Addl. bytes
        header[15] = 0x24; // -----

        header[16] = 0x20; // Source Address
        header[17] = 0x01; // -----
        header[18] = 0x0d; // -----
        header[19] = (byte) 0xb8; // -----
        header[20] = (byte) 0x85; // -----
        header[21] = (byte) 0xa3; // -----
        header[22] = 0x00; // -----
        header[23] = 0x00; // -----
        header[24] = 0x00; // -----
        header[25] = 0x00; // -----
        header[26] = (byte) 0x8a; // -----
        header[27] = 0x2e; // -----
        header[28] = 0x03; // -----
        header[29] = 0x70; // -----
        header[30] = 0x73; // -----
        header[31] = 0x34; // -----

        header[32] = 0x10; // Destination Address
        header[33] = 0x50; // -----
        header[34] = 0x00; // -----
        header[35] = 0x00; // -----
        header[36] = 0x00; // -----
        header[37] = 0x00; // -----
        header[38] = 0x00; // -----
        header[39] = 0x00; // -----
        header[40] = 0x00; // -----
        header[41] = 0x05; // -----
        header[42] = 0x06; // -----
        header[43] = 0x00; // -----
        header[44] = 0x30; // -----
        header[45] = 0x0c; // -----
        header[46] = 0x32; // -----
        header[47] = 0x6b; // -----

        header[48] = (byte) 0x27; // Source Port 10006
        header[49] = 0x16; // -----

        header[50] = 0x01; // Destination Port 443
        header[51] = (byte) 0xbb; // -----

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(Server.CONN_DIMENSIONS).set(Attrs.newInstance());
        channel.pipeline()
                .addLast(HAProxyMessageDecoder.class.getSimpleName(), new HAProxyMessageDecoder())
                .addLast(HAProxyMessageChannelHandler.class.getSimpleName(), new HAProxyMessageChannelHandler());

        channel.writeInbound(Unpooled.wrappedBuffer(header));

        Object result = channel.readInbound();
        assertNull(result);

        HAProxyMessage hapm =
                channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_MESSAGE).get();

        assertThat(hapm.sourceAddress()).isEqualTo("2001:db8:85a3:0:0:8a2e:370:7334");
        assertThat(hapm.destinationAddress()).isEqualTo("1050:0:0:0:5:600:300c:326b");
        assertThat(hapm.sourcePort()).isEqualTo(10006);
        assertThat(hapm.destinationPort()).isEqualTo(443);

        List<HAProxyTLV> customTLV = channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_CUSTOM_TLVS)
                .get();
        assertThat(customTLV.isEmpty()).isEqualTo(true);
    }
}
