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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.unix.Errors;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * User: Mike Smith
 * Date: 9/24/16
 * Time: 2:41 PM
 */
public final class PassportStateServerHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PassportStateServerHandler.class);

    private static Registry registry;

    private static CurrentPassport passport(ChannelHandlerContext ctx)
    {
        return CurrentPassport.fromChannel(ctx.channel());
    }

    public static void setRegistry(Registry registry) {
        checkNotNull(registry, "registry");
        checkState(
                PassportStateServerHandler.registry == null || PassportStateServerHandler.registry == registry,
                "registry already set");
        PassportStateServerHandler.registry = registry;
    }

    protected static void incrementExceptionCounter(Throwable throwable, String handler) {
        checkState(PassportStateServerHandler.registry != null, "registry not set");
        registry.counter("server.connection.exception",
                "handler", handler,
                "id", throwable.getClass().getSimpleName())
                .increment();
    }

    public static final class InboundHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_ACTIVE);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_INACTIVE);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_EXCEPTION);
            if (cause instanceof Errors.NativeIoException) {
                LOG.debug("PassportStateServerHandler Inbound NativeIoException " + cause);
                incrementExceptionCounter(cause, "PassportStateServerHandler.Inbound");
            } else {
                super.exceptionCaught(ctx, cause);
            }
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

    public static final class OutboundHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_CLOSE);
            super.close(ctx, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_DISCONNECT);
            super.disconnect(ctx, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            passport(ctx).add(PassportState.SERVER_CH_EXCEPTION);
            if (cause instanceof Errors.NativeIoException) {
                LOG.debug("PassportStateServerHandler Outbound NativeIoException " + cause);
                incrementExceptionCounter(cause, "PassportStateServerHandler.Outbound");
            } else {
                super.exceptionCaught(ctx, cause);
            }
        }
    }
}
