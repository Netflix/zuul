/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.zuul.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.integration.server.Bootstrap;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Objects;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Simple extension for managing the lifecycle of a zuul server for use in integration testing
 *
 * @author Justin Guerra
 * @since 6/9/25
 */
public class ZuulServerExtension implements AfterAllCallback, BeforeAllCallback {

    private final int eventLoopThreads;
    private final Duration originReadTimeout;

    private Bootstrap bootstrap;
    private int serverPort;

    private ZuulServerExtension(Builder builder) {
        this.eventLoopThreads = builder.eventLoopThreads;
        this.originReadTimeout = builder.originReadTimeout;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        serverPort = findAvailableTcpPort();

        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        config.setProperty("zuul.server.netty.socket.force_nio", "true");
        config.setProperty("zuul.server.netty.threads.worker", String.valueOf(eventLoopThreads));
        config.setProperty("zuul.server.port.main", serverPort);
        config.setProperty("api.ribbon." + CommonClientConfigKey.ReadTimeout.key(), originReadTimeout.toMillis());
        config.setProperty(
                "api.ribbon.NIWSServerListClassName", "com.netflix.zuul.integration.server.OriginServerList");

        // short circuit graceful shutdown
        config.setProperty("server.outofservice.close.timeout", "0");
        bootstrap = new Bootstrap();
        bootstrap.start();
        assertTrue(bootstrap.isRunning());
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (bootstrap != null) {
            bootstrap.stop();
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private static int findAvailableTcpPort() {
        try (ServerSocket sock = new ServerSocket(0)) {
            return sock.getLocalPort();
        } catch (IOException e) {
            return -1;
        }
    }

    public static class Builder {
        private int eventLoopThreads = 1;
        private Duration originReadTimeout;

        public Builder withEventLoopThreads(int eventLoopThreads) {
            this.eventLoopThreads = eventLoopThreads;
            return this;
        }

        public Builder withOriginReadTimeout(Duration originReadTimeout) {
            this.originReadTimeout = originReadTimeout;
            return this;
        }

        public ZuulServerExtension build() {
            Objects.requireNonNull(originReadTimeout, "originReadTimeout cannot be null");
            return new ZuulServerExtension(this);
        }
    }
}
