/**
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.netty.server;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.StatusChangeEvent;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class OutOfServiceConnectionsShutdown {

    private static final Logger LOG = LoggerFactory.getLogger(OutOfServiceConnectionsShutdown.class);

    private static final DynamicBooleanProperty ENABLED = new DynamicBooleanProperty("server.outofservice.connections.shutdown", false);
    private static final DynamicIntProperty DELAY_AFTER_OUT_OF_SERVICE_MS =
            new DynamicIntProperty("server.outofservice.connections.delay", 2000);

    private final EurekaClient discoveryClient;
    private final EventExecutor executor;
    private final Server server;

    public OutOfServiceConnectionsShutdown(EurekaClient discoveryClient,
                                           EventExecutor executor,
                                           Server server) {
        this.discoveryClient = discoveryClient;
        this.executor = executor;
        this.server = server;
        initDiscoveryListener();
    }

    private void initDiscoveryListener() {
        this.discoveryClient.registerEventListener(event -> {
            if (event instanceof StatusChangeEvent) {
                StatusChangeEvent sce = (StatusChangeEvent) event;

                LOG.info("Received " + sce.toString());

                if (sce.getPreviousStatus() == InstanceInfo.InstanceStatus.UP && (
                        sce.getStatus() == InstanceInfo.InstanceStatus.OUT_OF_SERVICE ||
                                sce.getStatus() == InstanceInfo.InstanceStatus.DOWN)) {
                    // TODO - Also should stop accepting any new client connections now too?

                    // Schedule to gracefully close all the client connections.
                    if (ENABLED.get()) {
                        executor.schedule(server::gracefullyShutdownConnections,
                                DELAY_AFTER_OUT_OF_SERVICE_MS.get(),
                                TimeUnit.MILLISECONDS);
                    }
                }
            }
        });

        LOG.info("Started listening for events from Eureka");
    }

}
