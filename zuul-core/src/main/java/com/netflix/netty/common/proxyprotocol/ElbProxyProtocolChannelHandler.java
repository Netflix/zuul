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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;

/**
 * Decides if we need to decode a HAProxyMessage. If so, adds the decoder followed by the handler.
 * Else, removes itself from the pipeline.
 */
public final class ElbProxyProtocolChannelHandler extends ChannelInboundHandlerAdapter {

    public static final String NAME = ElbProxyProtocolChannelHandler.class.getSimpleName();
    private final boolean withProxyProtocol;

    public ElbProxyProtocolChannelHandler(boolean withProxyProtocol) {
        this.withProxyProtocol = withProxyProtocol;
    }

    public void addProxyProtocol(ChannelPipeline pipeline) {
        pipeline.addLast(NAME, this);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (withProxyProtocol && isHAPMDetected(msg)) {
            ctx.pipeline().addAfter(NAME, null, new HAProxyMessageChannelHandler())
                    .replace(this, null, new HAProxyMessageDecoder());
        } else {
            ctx.pipeline().remove(this);
        }
        super.channelRead(ctx, msg);
    }

    private boolean isHAPMDetected(Object msg) {
        return HAProxyMessageDecoder.detectProtocol((ByteBuf) msg).state() == ProtocolDetectionState.DETECTED;
    }
}
