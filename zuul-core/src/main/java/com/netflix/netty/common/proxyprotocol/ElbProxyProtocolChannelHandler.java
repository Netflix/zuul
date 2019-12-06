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

import com.google.common.net.InetAddresses;
import com.netflix.netty.common.SourceAddressChannelHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies any decoded HAProxyMessage into the channel attributes, and doesn't pass
 * it any further along the pipeline.
 *
 * Use in conjunction with OptionalHAProxyMessageDecoder if ProxyProtocol is enabled on the ELB.
 *
 * User: michaels@netflix.com
 * Date: 3/24/16
 * Time: 11:59 AM
 */
public class ElbProxyProtocolChannelHandler extends ChannelInboundHandlerAdapter
{
    public static final String NAME = "ElbProxyProtocolChannelHandler";
    public static final AttributeKey<HAProxyMessage> ATTR_HAPROXY_MESSAGE = AttributeKey.newInstance("_haproxy_message");
    public static final AttributeKey<HAProxyProtocolVersion> ATTR_HAPROXY_VERSION = AttributeKey.newInstance("_haproxy_version");

    private static final Logger logger = LoggerFactory.getLogger("ElbProxyProtocolChannelHandler");

    private final boolean withProxyProtocol;

    public ElbProxyProtocolChannelHandler(boolean withProxyProtocol)
    {
        this.withProxyProtocol = withProxyProtocol;
    }

    /**
     * Setup the required handlers on pipeline using this method.
     *
     * @param pipeline
     */
    public void addProxyProtocol(ChannelPipeline pipeline)
    {
        pipeline.addLast(NAME, this);

        if (withProxyProtocol) {
            pipeline.addBefore(NAME, OptionalHAProxyMessageDecoder.NAME, new OptionalHAProxyMessageDecoder());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (withProxyProtocol) {
            if (msg instanceof HAProxyMessage && msg != null) {
                HAProxyMessage hapm = (HAProxyMessage) msg;
                Channel channel = ctx.channel();
                channel.attr(ATTR_HAPROXY_MESSAGE).set(hapm);
                ctx.channel().closeFuture().addListener((ChannelFutureListener) future -> {
                    if (hapm instanceof ReferenceCounted) {
                        hapm.release();
                    }
                });
                channel.attr(ATTR_HAPROXY_VERSION).set(hapm.protocolVersion());
                // Get the real host and port that the client connected to ELB with.
                String destinationAddress = hapm.destinationAddress();
                if (destinationAddress != null) {
                    channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).set(destinationAddress);
                    channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_PORT).set(hapm.destinationPort());

                    SocketAddress addr;
                    out: {
                        switch (hapm.proxiedProtocol()) {
                            case UNKNOWN:
                                throw new IllegalArgumentException("unknown proxy protocl" + destinationAddress);
                            case TCP4:
                            case TCP6:
                                addr = new InetSocketAddress(
                                        InetAddresses.forString(destinationAddress), hapm.destinationPort());
                                break out;
                            case UNIX_STREAM: // TODO: implement
                            case UDP4:
                            case UDP6:
                            case UNIX_DGRAM:
                                throw new IllegalArgumentException("unknown proxy protocol" + destinationAddress);
                        }
                        throw new AssertionError(hapm.proxiedProtocol());
                    }
                    channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDR).set(addr);
                }

                // Get the real client IP from the ProxyProtocol message sent by the ELB, and overwrite the SourceAddress
                // channel attribute.
                String sourceAddress = hapm.sourceAddress();
                if (sourceAddress != null) {
                    channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).set(sourceAddress);
                    channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_PORT).set(hapm.sourcePort());

                    SocketAddress addr;
                    out: {
                        switch (hapm.proxiedProtocol()) {
                            case UNKNOWN:
                                throw new IllegalArgumentException("unknown proxy protocl" + sourceAddress);
                            case TCP4:
                            case TCP6:
                                addr = new InetSocketAddress(
                                        InetAddresses.forString(sourceAddress), hapm.sourcePort());
                                break out;
                            case UNIX_STREAM: // TODO: implement
                            case UDP4:
                            case UDP6:
                            case UNIX_DGRAM:
                                throw new IllegalArgumentException("unknown proxy protocol" + sourceAddress);
                        }
                        throw new AssertionError(hapm.proxiedProtocol());
                    }
                    channel.attr(SourceAddressChannelHandler.ATTR_REMOTE_ADDR).set(addr);
                }

                // TODO - fire an additional event to notify interested parties that we now know the IP?

                // Remove ourselves (this handler) from the channel now, as no more work to do.
                ctx.pipeline().remove(this);

                // Do not continue propagating the message.
                return;
            }
        }

        super.channelRead(ctx, msg);
    }
}
