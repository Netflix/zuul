/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.integration.server;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.Server;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private Server server;
    private int exitCode = 0;

    public void start() {
        long startNanos = System.nanoTime();
        logger.info("Zuul: starting up.");

        try {
            Injector injector = Guice.createInjector(new ServerModule());
            BaseServerStartup serverStartup = injector.getInstance(BaseServerStartup.class);
            server = serverStartup.server();

            server.start();
            long startupDuration = System.nanoTime() - startNanos;
            logger.info("Zuul: finished startup. Duration = {}ms", TimeUnit.NANOSECONDS.toMillis(startupDuration));
            // server.awaitTermination();
        } catch (Throwable t) {
            exitCode = 1;
            throw new RuntimeException(t);
        }
    }

    public Server getServer() {
        return this.server;
    }

    public boolean isRunning() {
        return (server != null) && (server.getListeningAddresses().size() > 0);
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
