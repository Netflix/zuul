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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HAProxyMessageChannelHandlerTest {

    @Test
    public void setClientDestPortForHAPM() {
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

        InetSocketAddress destAddress = channel
                .attr(SourceAddressChannelHandler.ATTR_PROXY_PROTOCOL_DESTINATION_ADDRESS).get();

        InetSocketAddress srcAddress = (InetSocketAddress) channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR)
                .get();

        assertEquals("124.123.111.111", destAddress.getHostString());
        assertEquals(443, destAddress.getPort());

        assertEquals("192.168.0.1", srcAddress.getHostString());
        assertEquals(10008, srcAddress.getPort());

        Attrs attrs = channel.attr(Server.CONN_DIMENSIONS).get();
        Integer port = HAProxyMessageChannelHandler.HAPM_DEST_PORT.get(attrs);
        assertEquals(443, port.intValue());
    }
}