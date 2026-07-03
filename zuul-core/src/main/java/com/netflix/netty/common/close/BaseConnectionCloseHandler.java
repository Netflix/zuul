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
 * Base handler responsible for orchestrating {@link ConnectionCloseEvent}s. It must sit on the connection-level
 * pipeline (the parent pipeline for http/2); concrete subclasses supply the actual close behavior via
 * {@link #onCloseEvent}.
 *
 * <p>On receiving a {@link ConnectionCloseEvent} this handler invokes
 * {@link #onCloseEvent(ChannelHandlerContext, ConnectionCloseEvent)} at most once - immediately for a
 * {@link ConnectionCloseEvent.Graceful} event, or after a random jitter delay for a
 * {@link ConnectionCloseEvent.GracefulDelayed} event. An {@code onCloseEvent} implementation should either close the
 * channel outright or begin a graceful close, using {@link #scheduleCloseTimeout(Runnable, Channel)} to guarantee the
 * connection is eventually closed.
 *
 * @author Justin Guerra
 * @since 6/25/26
 */
@NullMarked
abstract class BaseConnectionCloseHandler extends ChannelDuplexHandler {

    protected final Registry registry;

    @Nullable
    private ConnectionCloseEvent closeEvent;

    @Nullable
    protected ScheduledFuture<?> jitterFuture;

    @Nullable
    protected ScheduledFuture<?> forceCloseFuture;

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
        if (jitterFuture != null) {
            jitterFuture.cancel(false);
            jitterFuture = null;
        }

        if (forceCloseFuture != null) {
            forceCloseFuture.cancel(false);
            forceCloseFuture = null;
        }

        ctx.fireChannelInactive();
    }

    protected abstract String getProtocol();

    protected abstract void onCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event);

    protected void scheduleCloseTimeout(Runnable closeTask, Channel channel) {
        if (!channel.isActive() || forceCloseFuture != null) {
            return;
        }

        forceCloseFuture = channel.eventLoop().schedule(closeTask, getCloseTimeout(channel), TimeUnit.SECONDS);
    }

    protected int getPort(Channel channel) {
        Integer port =
                channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).get();
        return port != null ? port : -1;
    }

    protected boolean isFlaggedForClose() {
        return closeEvent != null;
    }

    protected void countHandled(int port, String trigger) {
        if (closeEvent == null) {
            return;
        }

        Id tags = registry.createId("server.connection.close.handled")
                .withTag("close_type", closeEvent.closeType())
                .withTag("close_reason", closeEvent.reason().name())
                .withTag("close_trigger", trigger)
                .withTag("port", Integer.toString(port))
                .withTag("protocol", getProtocol());
        registry.counter(tags).increment();
    }

    private int getCloseTimeout(Channel channel) {
        ChannelConfig channelConfig =
                channel.attr(BaseZuulChannelInitializer.ATTR_CHANNEL_CONFIG).get();
        if (channelConfig == null) {
            return CommonChannelConfigKeys.connCloseDelay.defaultValue();
        }

        return channelConfig.get(CommonChannelConfigKeys.connCloseDelay);
    }

    private void scheduleDelayedClose(
            ChannelHandlerContext ctx, Runnable task, ConnectionCloseEvent.GracefulDelayed event) {
        if (!ctx.channel().isActive() || jitterFuture != null) {
            return;
        }

        long jitter = ThreadLocalRandom.current().nextLong(event.maxJitter().toMillis());
        jitterFuture = ctx.executor().schedule(task, jitter, TimeUnit.MILLISECONDS);
    }

    private void countStarted(int port) {
        if (closeEvent == null) {
            return;
        }

        Id tags = registry.createId("server.connection.close.started")
                .withTag("close_type", closeEvent.closeType())
                .withTag("close_reason", closeEvent.reason().name())
                .withTag("port", Integer.toString(port))
                .withTag("protocol", getProtocol());
        registry.counter(tags).increment();
    }

    private void handleCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event) {
        if (isFlaggedForClose()) {
            return;
        }

        closeEvent = event;
        countStarted(getPort(ctx.channel()));
        onCloseEvent(ctx, event);
    }
}
