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
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicStringListProperty;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.spectator.api.Registry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import lombok.NonNull;

/**
 * Decides if we need to decode a HAProxyMessage. If so, adds the decoder followed by the handler.
 * Else, removes itself from the pipeline.
 */
public final class ElbProxyProtocolChannelHandler extends ChannelInboundHandlerAdapter {

    public static final String NAME = ElbProxyProtocolChannelHandler.class.getSimpleName();

    @VisibleForTesting
    static DynamicStringListProperty TRUSTED_PEER_CIDRS =
            new DynamicStringListProperty("zuul.proxyprotocol.trusted.cidrs", "");

    @VisibleForTesting
    static DynamicBooleanProperty ENFORCE_TRUSTED_PEER_CIDRS =
            new DynamicBooleanProperty("zuul.proxyprotocol.trusted.cidrs.enforce", false);

    private final boolean withProxyProtocol;
    private final Registry registry;

    public ElbProxyProtocolChannelHandler(@NonNull Registry registry, boolean withProxyProtocol) {
        this.withProxyProtocol = withProxyProtocol;
        this.registry = registry;
    }

    public void addProxyProtocol(ChannelPipeline pipeline) {
        pipeline.addLast(NAME, this);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!withProxyProtocol) {
            ctx.pipeline().remove(this);
            super.channelRead(ctx, msg);
            return;
        }

        if (!isTrustedPeer(ctx.channel())) {
            int port = ctx.channel()
                    .attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT)
                    .get();
            boolean enforce = ENFORCE_TRUSTED_PEER_CIDRS.get();
            registry.counter(
                            "zuul.hapm.untrusted_peer",
                            "port",
                            String.valueOf(port),
                            "enforced",
                            String.valueOf(enforce))
                    .increment();
            if (enforce) {
                ctx.pipeline().remove(this);
                super.channelRead(ctx, msg);
                return;
            }
        }

        ProtocolDetectionState haProxyState = getDetectionState(msg);
        if (haProxyState == ProtocolDetectionState.DETECTED) {
            ctx.pipeline()
                    .addAfter(NAME, null, new HAProxyMessageChannelHandler())
                    .replace(this, null, new HAProxyMessageDecoder());
        } else {
            int port = ctx.channel()
                    .attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT)
                    .get();

            // This likely means initialization was requested with proxy protocol, but we encountered a non-ppv2
            // message
            registry.counter(
                            "zuul.hapm.decode",
                            "success",
                            "false",
                            "port",
                            String.valueOf(port),
                            "needs_more_data",
                            String.valueOf(haProxyState == ProtocolDetectionState.NEEDS_MORE_DATA))
                    .increment();
            ctx.pipeline().remove(this);
        }
        super.channelRead(ctx, msg);
    }

    /**
     * Returns true if no trusted CIDR allowlist is configured (preserving legacy behavior), or if the channel's
     * remote peer falls within one of the configured CIDRs.
     */
    @VisibleForTesting
    static boolean isTrustedPeer(Channel channel) {
        List<String> cidrs = TRUSTED_PEER_CIDRS.get();
        if (cidrs.isEmpty()) {
            return true;
        }
        SocketAddress remoteAddress = channel.remoteAddress();
        if (!(remoteAddress instanceof InetSocketAddress inetRemoteAddress) || inetRemoteAddress.getAddress() == null) {
            return false;
        }
        for (String cidr : cidrs) {
            if (matchesCidr(inetRemoteAddress, cidr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(InetSocketAddress remoteAddress, String cidr) {
        int slash = cidr.indexOf('/');
        if (slash < 0) {
            return false;
        }
        try {
            String ipAddress = cidr.substring(0, slash);
            int prefix = Integer.parseInt(cidr.substring(slash + 1));
            return new IpSubnetFilterRule(ipAddress, prefix, IpFilterRuleType.ACCEPT).matches(remoteAddress);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ProtocolDetectionState getDetectionState(Object msg) {
        return HAProxyMessageDecoder.detectProtocol((ByteBuf) msg).state();
    }
}
