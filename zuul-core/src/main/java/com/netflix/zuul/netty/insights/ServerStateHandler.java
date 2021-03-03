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

package com.netflix.zuul.netty.insights;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.unix.Errors;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * User: Mike Smith Date: 9/24/16 Time: 2:41 PM
 */
public final class ServerStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerStateHandler.class);

    private static CurrentPassport passport(ChannelHandlerContext ctx) {
        return CurrentPassport.fromChannel(ctx.channel());
    }

    public static final class InboundHandler extends ChannelInboundHandlerAdapter {

        private final Registry registry;
        private final Counter totalConnections;
        private final Counter connectionClosed;
        private final Counter connectionErrors;

        public InboundHandler(Registry registry, String metricId) {
            this.registry = registry;
            this.totalConnections = registry.counter("server.connections.connect", "id", metricId);
            this.connectionClosed = registry.counter("server.connections.close", "id", metricId);
            this.connectionErrors = registry.counter("server.connections.errors", "id", metricId);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            totalConnections.increment();
            passport(ctx).add(PassportState.SERVER_CH_ACTIVE);

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connectionClosed.increment();
            passport(ctx).add(PassportState.SERVER_CH_INACTIVE);

            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            connectionErrors.increment();
            registry.counter("server.connection.exception.inbound",
                    "handler", "ServerStateHandler.InboundHandler",
                    "id", cause.getClass().getSimpleName())
                    .increment();
            passport(ctx).add(PassportState.SERVER_CH_EXCEPTION);
            logger.info("Connection error on Inbound: {} ", cause);

            super.exceptionCaught(ctx, cause);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                final CurrentPassport passport = CurrentPassport.fromChannel(ctx.channel());
                if (passport != null) {
                    passport.add(PassportState.SERVER_CH_IDLE_TIMEOUT);
                }
            }

            super.userEventTriggered(ctx, evt);
        }
    }

    public static final class OutboundHandler extends ChannelOutboundHandlerAdapter {

        private final Registry registry;

        public OutboundHandler(Registry registry) {
            this.registry = registry;
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            passport(ctx).add(PassportState.SERVER_CH_CLOSE);
            super.close(ctx, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            passport(ctx).add(PassportState.SERVER_CH_DISCONNECT);
            super.disconnect(ctx, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            passport(ctx).add(PassportState.SERVER_CH_EXCEPTION);
            if (cause instanceof Errors.NativeIoException) {
                logger.debug("PassportStateServerHandler Outbound NativeIoException " + cause);
                registry.counter("server.connection.exception.outbound",
                        "handler", "ServerStateHandler.OutboundHandler",
                        "id", cause.getClass().getSimpleName())
                        .increment();

            } else {
                super.exceptionCaught(ctx, cause);
            }
        }
    }
}
