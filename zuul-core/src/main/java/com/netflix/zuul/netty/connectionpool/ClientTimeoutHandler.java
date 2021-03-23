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

package com.netflix.zuul.netty.connectionpool;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Timeout Handler
 *
 * Author: Arthur Gonigberg
 * Date: July 01, 2019
 */
public final class ClientTimeoutHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ClientTimeoutHandler.class);

    public static final AttributeKey<Duration> ORIGIN_RESPONSE_READ_TIMEOUT = AttributeKey.newInstance("originResponseReadTimeout");

    public static final class InboundHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                if (msg instanceof LastHttpContent) {
                    LOG.debug("[{}] Removing read timeout handler", ctx.channel().id());
                    PooledConnection.getFromChannel(ctx.channel()).removeReadTimeoutHandler();
                }
            }
            finally {
                super.channelRead(ctx, msg);
            }
        }
    }

    public static final class OutboundHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            try {
                final Duration timeout = ctx.channel().attr(ORIGIN_RESPONSE_READ_TIMEOUT).get();
                if (timeout != null && msg instanceof LastHttpContent) {
                    promise.addListener(e -> {
                        LOG.debug("[{}] Adding read timeout handler: {}", ctx.channel().id(), timeout.toMillis());
                        PooledConnection.getFromChannel(ctx.channel()).startReadTimeoutHandler(timeout);
                    });
                }
            }
            finally {
                super.write(ctx, msg, promise);
            }
        }
    }
}
