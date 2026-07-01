/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.netty.common.close;

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.close.ConnectionCloseEvent.Graceful;
import com.netflix.netty.common.close.ConnectionCloseEvent.GracefulDelayed;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.server.BaseZuulChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * @author Justin Guerra
 * @since 6/25/26
 */
@NullMarked
abstract class BaseConnectionCloseHandler extends ChannelDuplexHandler {

    protected final Registry registry;

    @Nullable
    private ConnectionCloseEvent closeEvent;

    @Nullable
    protected ScheduledFuture<?> delayedClose;

    @Nullable
    protected ScheduledFuture<?> closeTimeout;

    BaseConnectionCloseHandler(Registry registry) {
        this.registry = registry;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ConnectionCloseEvent event && !isFlaggedForClose()) {
            switch (event) {
                case ConnectionCloseEvent.Graceful ignored -> {
                    handleCloseEvent(ctx, event);
                }
                case ConnectionCloseEvent.GracefulDelayed delayed -> {
                    scheduleDelayedClose(ctx, () -> handleCloseEvent(ctx, event), delayed);
                }
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (delayedClose != null) {
            delayedClose.cancel(false);
            delayedClose = null;
        }

        if (closeTimeout != null) {
            closeTimeout.cancel(false);
            closeTimeout = null;
        }

        ctx.fireChannelInactive();
    }

    protected abstract String getProtocol();

    protected abstract void handleCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event);

    protected void scheduleDelayedClose(
            ChannelHandlerContext ctx, Runnable task, ConnectionCloseEvent.GracefulDelayed event) {
        if (!ctx.channel().isActive() || delayedClose != null) {
            return;
        }

        long jitter = ThreadLocalRandom.current().nextLong(event.maxJitter().toMillis());
        delayedClose = ctx.executor().schedule(task, jitter, TimeUnit.MILLISECONDS);
    }

    protected void scheduleCloseTimeout(Runnable closeTask, Channel channel) {
        if (!channel.isActive() || closeTimeout != null) {
            return;
        }

        closeTimeout = channel.eventLoop().schedule(closeTask, getCloseTimeout(channel), TimeUnit.SECONDS);
    }

    protected int getPort(Channel channel) {
        Integer port =
                channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).get();
        return port != null ? port : -1;
    }

    private int getCloseTimeout(Channel channel) {
        ChannelConfig channelConfig =
                channel.attr(BaseZuulChannelInitializer.ATTR_CHANNEL_CONFIG).get();
        if (channelConfig == null) {
            return CommonChannelConfigKeys.connCloseDelay.defaultValue();
        }

        return channelConfig.get(CommonChannelConfigKeys.connCloseDelay);
    }

    protected void flagForClose(ConnectionCloseEvent event) {
        this.closeEvent = event;
    }

    protected boolean isFlaggedForClose() {
        return closeEvent != null;
    }

    protected void countFlagged(int port) {
        if (closeEvent == null) {
            return;
        }

        Id tags = registry.createId("server.connection.close.started")
                .withTag("close_type", closeType(closeEvent))
                .withTag("close_reason", closeEvent.reason().name())
                .withTag("port", Integer.toString(port))
                .withTag("protocol", getProtocol());
        registry.counter(tags).increment();
    }

    protected void countHandled(int port, String trigger) {
        if (closeEvent == null) {
            return;
        }

        Id tags = registry.createId("server.connection.close.handled")
                .withTag("close_type", closeType(closeEvent))
                .withTag("close_reason", closeEvent.reason().name())
                .withTag("close_trigger", trigger)
                .withTag("port", Integer.toString(port))
                .withTag("protocol", getProtocol());
        registry.counter(tags).increment();
    }

    private static String closeType(ConnectionCloseEvent event) {
        return switch (event) {
            case Graceful ignored -> "GRACEFUL";
            case GracefulDelayed ignored -> "GRACEFUL_DELAYED";
        };
    }
}
