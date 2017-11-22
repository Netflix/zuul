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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.netty.common.ConnectionCloseType;
import io.netty.channel.ChannelFuture;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * User: michaels@netflix.com
 * Date: 3/6/17
 * Time: 12:36 PM
 */
public class ClientConnectionsShutdown
{
    private static final Logger LOG = LoggerFactory.getLogger(ClientConnectionsShutdown.class);
    private static final DynamicBooleanProperty ENABLED = new DynamicBooleanProperty("server.outofservice.connections.shutdown", false);
    private static final DynamicIntProperty DELAY_AFTER_OUT_OF_SERVICE_MS =
            new DynamicIntProperty("server.outofservice.connections.delay", 2000);

    private final ChannelGroup channels;
    private final EventExecutor executor;
    private final EurekaClient discoveryClient;

    public ClientConnectionsShutdown(ChannelGroup channels, EventExecutor executor, EurekaClient discoveryClient)
    {
        this.channels = channels;
        this.executor = executor;
        this.discoveryClient = discoveryClient;

        if (discoveryClient != null)
            initDiscoveryListener();
    }

    private void initDiscoveryListener()
    {
        this.discoveryClient.registerEventListener(event -> {
            if (event instanceof StatusChangeEvent) {
                StatusChangeEvent sce = (StatusChangeEvent) event;

                LOG.info("Received " + sce.toString());

                if (sce.getPreviousStatus() == InstanceInfo.InstanceStatus.UP
                    && (sce.getStatus() == InstanceInfo.InstanceStatus.OUT_OF_SERVICE || sce.getStatus() == InstanceInfo.InstanceStatus.DOWN))
                {
                    // Schedule to gracefully close all the client connections.
                    if (ENABLED.get()) {
                        executor.schedule(() -> {
                            gracefullyShutdownClientChannels();
                        }, DELAY_AFTER_OUT_OF_SERVICE_MS.get(), TimeUnit.MILLISECONDS);
                    }
                }
            }
        });
    }

    /**
     * Note this blocks until all the channels have finished closing.
     */
    public void gracefullyShutdownClientChannels()
    {
        LOG.warn("Gracefully shutting down all client channels");
        try {
            List<ChannelFuture> futures = new ArrayList<>();
            channels.forEach(channel -> {
                ConnectionCloseType.setForChannel(channel, ConnectionCloseType.DELAYED_GRACEFUL);
                ChannelFuture f = channel.pipeline().close();
                futures.add(f);
            });

            LOG.warn("Waiting for " + futures.size() + " client channels to be closed.");
            for (ChannelFuture f : futures) {
                f.await();
            }
            LOG.warn(futures.size() + " client channels closed.");
        }
        catch (InterruptedException ie) {
            LOG.warn("Interrupted while shutting down client channels");
        }
    }
}
