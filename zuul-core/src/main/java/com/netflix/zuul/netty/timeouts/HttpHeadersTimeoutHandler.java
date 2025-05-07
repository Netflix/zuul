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

package com.netflix.zuul.netty.timeouts;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Timer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;

public class HttpHeadersTimeoutHandler {
        private static final Logger LOG = LoggerFactory.getLogger(HttpHeadersTimeoutHandler.class);

        private static final AttributeKey<ScheduledFuture<Void>> HTTP_HEADERS_READ_TIMEOUT_FUTURE =
            AttributeKey.newInstance("httpHeadersReadTimeoutFuture");
        private static final AttributeKey<Long> HTTP_HEADERS_READ_START_TIME =
            AttributeKey.newInstance("httpHeadersReadStartTime");

        public static class InboundHandler extends ChannelInboundHandlerAdapter {
            private final BooleanSupplier httpHeadersReadTimeoutEnabledSupplier;
            private final IntSupplier httpHeadersReadTimeoutSupplier;

            private final Counter httpHeadersReadTimeoutCounter;
            private final Timer httpHeadersReadTimer;

            private boolean closed = false;

            public InboundHandler(BooleanSupplier httpHeadersReadTimeoutEnabledSupplier, IntSupplier httpHeadersReadTimeoutSupplier, Counter httpHeadersReadTimeoutCounter, Timer httpHeadersReadTimer) {
                this.httpHeadersReadTimeoutEnabledSupplier = httpHeadersReadTimeoutEnabledSupplier;
                this.httpHeadersReadTimeoutSupplier = httpHeadersReadTimeoutSupplier;
                this.httpHeadersReadTimeoutCounter = httpHeadersReadTimeoutCounter;
                this.httpHeadersReadTimer = httpHeadersReadTimer;
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                try {
                    ctx.channel().attr(HTTP_HEADERS_READ_START_TIME).set(System.nanoTime());
                    if (!httpHeadersReadTimeoutEnabledSupplier.getAsBoolean())
                        return;
                    int timeout = httpHeadersReadTimeoutSupplier.getAsInt();
                    ctx.channel().attr(HTTP_HEADERS_READ_TIMEOUT_FUTURE).set(
                        ctx.executor().schedule(
                            () -> {
                                if (!closed) {
                                    ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
                                    ctx.close();
                                    closed = true;
                                    httpHeadersReadTimeoutCounter.increment();
                                    LOG.debug("[{}] HTTP headers read timeout handler timed out", ctx.channel().id());
                                }
                                return null;
                            },
                            timeout,
                            TimeUnit.MILLISECONDS
                        )
                    );
                    LOG.debug("[{}] Adding HTTP headers read timeout handler: {}", ctx.channel().id(), timeout);
                } finally {
                    super.channelActive(ctx);
                }
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                try {
                    if (msg instanceof HttpMessage) {
                        httpHeadersReadTimer.record(System.nanoTime() - ctx.channel().attr(HTTP_HEADERS_READ_START_TIME).get(), TimeUnit.NANOSECONDS);
                        ScheduledFuture<Void> future = ctx.channel().attr(HTTP_HEADERS_READ_TIMEOUT_FUTURE).get();
                        if (future != null) {
                            future.cancel(false);
                            LOG.debug("[{}] Removing HTTP headers read timeout handler", ctx.channel().id());
                        }
                        ctx.pipeline().remove(this);
                    }
                } finally {
                    super.channelRead(ctx, msg);
                }
            }
        }
}
