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

package com.netflix.zuul.netty.server;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.netty.common.close.CloseReason;
import com.netflix.netty.common.close.ConnectionCloseEvent;
import com.netflix.netty.common.close.ConnectionCloseEvent.GracefulDelayed;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 3/6/17
 * Time: 12:36 PM
 */
@NullMarked
public class ClientConnectionsShutdown {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConnectionsShutdown.class);
    private static final DynamicBooleanProperty ENABLED =
            new DynamicBooleanProperty("server.outofservice.connections.shutdown", false);
    private static final DynamicIntProperty DELAY_AFTER_OUT_OF_SERVICE_MS =
            new DynamicIntProperty("server.outofservice.connections.delay", 2000);
    private static final DynamicIntProperty GRACEFUL_CLOSE_TIMEOUT =
            new DynamicIntProperty("server.outofservice.close.timeout", 30);

    private final ChannelGroup channels;
    private final EventExecutor executor;

    @Nullable
    private final EurekaClient discoveryClient;

    public ClientConnectionsShutdown(
            ChannelGroup channels, EventExecutor executor, @Nullable EurekaClient discoveryClient) {
        this.channels = channels;
        this.executor = executor;
        this.discoveryClient = discoveryClient;

        if (discoveryClient != null) {
            initDiscoveryListener();
        }
    }

    private void initDiscoveryListener() {
        if (discoveryClient == null) {
            return;
        }

        this.discoveryClient.registerEventListener(event -> {
            if (event instanceof StatusChangeEvent sce) {

                LOG.info("Received {}", sce);

                if (sce.getPreviousStatus() == InstanceStatus.UP
                        && (sce.getStatus() == InstanceStatus.OUT_OF_SERVICE
                                || sce.getStatus() == InstanceStatus.DOWN)) {
                    // Schedule to gracefully close all the client connections.
                    if (ENABLED.get()) {
                        executor.schedule(
                                () -> gracefullyShutdownClientChannels(CloseReason.OUT_OF_SERVICE),
                                DELAY_AFTER_OUT_OF_SERVICE_MS.get(),
                                TimeUnit.MILLISECONDS);
                    }
                }
            }
        });
    }

    public Promise<@Nullable Void> gracefullyShutdownClientChannels() {
        return gracefullyShutdownClientChannels(CloseReason.SHUTDOWN);
    }

    Promise<@Nullable Void> gracefullyShutdownClientChannels(CloseReason closeReason) {
        // Mark all active connections to be closed after next response sent.
        LOG.warn("Flagging CLOSE_AFTER_RESPONSE on {} client channels.", channels.size());

        // racy situation if new connections are still coming in, but any channels created after newCloseFuture will
        // be closed during the force close stage
        ChannelGroupFuture closeFuture = channels.newCloseFuture();
        ConnectionCloseEvent closeEvent = new GracefulDelayed(closeReason, getJitterFor(closeReason));
        for (Channel channel : channels) {
            channel.pipeline().fireUserEventTriggered(closeEvent);
        }

        LOG.info("Setting up scheduled task for {} with shutdownType: {}", closeFuture, closeReason);
        Promise<@Nullable Void> promise = executor.newPromise();
        Runnable cancelTimeoutTask;
        if (closeReason == CloseReason.SHUTDOWN) {
            ScheduledFuture<?> timeoutTask = executor.schedule(
                    () -> {
                        LOG.warn("Force closing remaining {} active client channels.", channels.size());
                        channels.close().addListener(future -> {
                            if (!future.isSuccess()) {
                                LOG.error("Failed to close all connections", future.cause());
                            }
                            if (!promise.isDone()) {
                                promise.setSuccess(null);
                            }
                        });
                    },
                    GRACEFUL_CLOSE_TIMEOUT.get(),
                    TimeUnit.SECONDS);
            cancelTimeoutTask = () -> {
                if (!timeoutTask.isDone()) {
                    LOG.info("Timeout task canceled before completion.");
                    // close happened before the timeout
                    timeoutTask.cancel(false);
                }
            };
        } else {
            cancelTimeoutTask = () -> {};
        }

        closeFuture.addListener(future -> {
            LOG.info("CloseFuture completed successfully: {}", future.isSuccess());
            cancelTimeoutTask.run();
            promise.setSuccess(null);
        });

        return promise;
    }

    protected Duration getJitterFor(CloseReason reason) {
        // configurable..
        return switch (reason) {
            case OUT_OF_SERVICE -> Duration.ofMinutes(10);
            default -> Duration.ofSeconds(60);
        };
    }
}
