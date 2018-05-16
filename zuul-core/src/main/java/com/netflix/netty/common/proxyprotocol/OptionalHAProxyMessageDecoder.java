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

import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.config.DynamicStringProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses whether a new connection is prefixed with the ProxyProtocol from an ELB. If it is, then
 * it adds a HAProxyMessageDecoder into the pipeline after THIS handler.
 *
 * User: michaels@netflix.com
 * Date: 3/24/16
 * Time: 3:13 PM
 */
public class OptionalHAProxyMessageDecoder extends ChannelInboundHandlerAdapter
{
    public static final String NAME = "OptionalHAProxyMessageDecoder";
    private static final Logger logger = LoggerFactory.getLogger("OptionalHAProxyMessageDecoder");
    private static final CachedDynamicBooleanProperty dumpHAProxyByteBuf = new CachedDynamicBooleanProperty("zuul.haproxy.dump.bytebuf", false);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof ByteBuf) {
            try {
                ProtocolDetectionResult<HAProxyProtocolVersion> result = HAProxyMessageDecoder.detectProtocol((ByteBuf) msg);

                // TODO - is it possible that this message could be split over multiple ByteBufS, and therefore this would fail?
                if (result.state() == ProtocolDetectionState.DETECTED) {
                    // Add the actual HAProxy decoder.
                    // Note that the HAProxyMessageDecoder will remove itself once it has finished decoding the initial ProxyProtocol message(s).
                    ctx.pipeline().addAfter(NAME, null, new HAProxyMessageDecoder());

                    // Remove this handler, as now no longer needed.
                    ctx.pipeline().remove(this);
                }
            } catch (Exception e) {
                if (msg != null) {
                    logger.error("Exception in OptionalHAProxyMessageDecoder {}" + e.getClass().getCanonicalName());
                    if (dumpHAProxyByteBuf.get()) {
                        logger.error("Exception Stack: {}" + e.getStackTrace());
                        logger.error("Bytebuf is:  {} " + ((ByteBuf) msg).toString(CharsetUtil.US_ASCII));
                    }
                    ((ByteBuf) msg).release();
                }
            }
        }
        super.channelRead(ctx, msg);
    }
}
