/*
 * Copyright 2018 Netflix, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyTLV;
import io.netty.util.AttributeKey;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Copies any decoded HAProxyMessage into the channel attributes, and doesn't pass it any further along the pipeline.
 * Use in conjunction with HAProxyMessageDecoder if proxy protocol is enabled on the ELB.
 */
public final class HAProxyMessageChannelHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<HAProxyMessage> ATTR_HAPROXY_MESSAGE =
            AttributeKey.newInstance("_haproxy_message");
    public static final AttributeKey<HAProxyProtocolVersion> ATTR_HAPROXY_VERSION =
            AttributeKey.newInstance("_haproxy_version");
    public static final AttributeKey<List<HAProxyTLV>> ATTR_HAPROXY_CUSTOM_TLVS =
            AttributeKey.newInstance("_haproxy_tlvs");

    @VisibleForTesting
    static final Attrs.Key<Integer> HAPM_DEST_PORT = Attrs.newKey("hapm_port");

    @VisibleForTesting
    static final Attrs.Key<String> HAPM_DEST_IP_VERSION = Attrs.newKey("hapm_dst_ipproto");

    @VisibleForTesting
    static final Attrs.Key<String> HAPM_SRC_IP_VERSION = Attrs.newKey("hapm_src_ipproto");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HAProxyMessage hapm) {
            Channel channel = ctx.channel();
            channel.attr(ATTR_HAPROXY_MESSAGE).set(hapm);
            ctx.channel().closeFuture().addListener((ChannelFutureListener) future -> hapm.release());
            channel.attr(ATTR_HAPROXY_VERSION).set(hapm.protocolVersion());
            // Parse and persist any custom TLVs that might be part of the connection
            List<HAProxyTLV> tlvList = hapm.tlvs().stream()
                    .filter(tlv -> tlv.type() == HAProxyTLV.Type.OTHER)
                    .collect(Collectors.toList());
            channel.attr(ATTR_HAPROXY_CUSTOM_TLVS).set(tlvList);
            // Get the real host and port that the client connected with.
            parseDstAddr(hapm, channel);
            parseSrcAddr(hapm, channel);
            // Remove ourselves (this handler) from the channel now, as this is conn. level info
            ctx.pipeline().remove(this);
        }
    }

    private void parseSrcAddr(HAProxyMessage hapm, Channel channel) {
        String sourceAddress = hapm.sourceAddress();
        if (sourceAddress != null) {
            channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).set(sourceAddress);

            SocketAddress srcAddr;
            switch (hapm.proxiedProtocol()) {
                case UNKNOWN:
                    throw new IllegalArgumentException("unknown proxy protocol" + sourceAddress);
                case TCP4:
                case TCP6:
                    InetSocketAddress inetAddr;
                    srcAddr =
                            inetAddr = new InetSocketAddress(InetAddresses.forString(sourceAddress), hapm.sourcePort());
                    Attrs attrs = channel.attr(Server.CONN_DIMENSIONS).get();
                    if (inetAddr.getAddress() instanceof Inet4Address) {
                        HAPM_SRC_IP_VERSION.put(attrs, "v4");
                    } else if (inetAddr.getAddress() instanceof Inet6Address) {
                        HAPM_SRC_IP_VERSION.put(attrs, "v6");
                    } else {
                        HAPM_SRC_IP_VERSION.put(attrs, "unknown");
                    }
                    break;
                case UNIX_STREAM: // TODO: implement
                case UDP4:
                case UDP6:
                case UNIX_DGRAM:
                    throw new IllegalArgumentException("unknown proxy protocol" + sourceAddress);
                default:
                    throw new AssertionError(hapm.proxiedProtocol());
            }
            channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).set(srcAddr);
        }
    }

    private void parseDstAddr(HAProxyMessage hapm, Channel channel) {
        String destinationAddress = hapm.destinationAddress();
        if (destinationAddress != null) {
            channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).set(destinationAddress);

            SocketAddress dstAddr;
            switch (hapm.proxiedProtocol()) {
                case UNKNOWN:
                    throw new IllegalArgumentException("unknown proxy protocol" + destinationAddress);
                case TCP4:
                case TCP6:
                    InetSocketAddress inetAddr =
                            new InetSocketAddress(InetAddresses.forString(destinationAddress), hapm.destinationPort());
                    dstAddr = inetAddr;
                    // set ppv2 attr explicitly because ATTR_LOCAL_ADDR could be non ppv2
                    channel.attr(SourceAddressChannelHandler.ATTR_PROXY_PROTOCOL_DESTINATION_ADDRESS)
                            .set(inetAddr);
                    Attrs attrs = channel.attr(Server.CONN_DIMENSIONS).get();
                    if (inetAddr.getAddress() instanceof Inet4Address) {
                        HAPM_DEST_IP_VERSION.put(attrs, "v4");
                    } else if (inetAddr.getAddress() instanceof Inet6Address) {
                        HAPM_DEST_IP_VERSION.put(attrs, "v6");
                    } else {
                        HAPM_DEST_IP_VERSION.put(attrs, "unknown");
                    }
                    HAPM_DEST_PORT.put(attrs, hapm.destinationPort());
                    break;
                case UNIX_STREAM: // TODO: implement
                case UDP4:
                case UDP6:
                case UNIX_DGRAM:
                    throw new IllegalArgumentException("unknown proxy protocol" + destinationAddress);
                default:
                    throw new AssertionError(hapm.proxiedProtocol());
            }
            channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).set(dstAddr);
        }
    }
}
