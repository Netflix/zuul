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

package com.netflix.zuul.sample;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.Server;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap
 *
 * Author: Arthur Gonigberg
 * Date: November 20, 2017
 */
public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    public static void main(String[] args) {
        new Bootstrap().start();
    }

    public void start() {
        long startNanos = System.nanoTime();
        logger.info("Zuul Sample: starting up.");
        int exitCode = 0;

        Server server = null;

        try {
            Injector injector = Guice.createInjector(new ZuulSampleModule());
            BaseServerStartup serverStartup = injector.getInstance(BaseServerStartup.class);
            server = serverStartup.server();

            server.start();
            long startupDuration = System.nanoTime() - startNanos;
            logger.info(
                    "Zuul Sample: finished startup. Duration = {}ms", TimeUnit.NANOSECONDS.toMillis(startupDuration));
            server.awaitTermination();
        } catch (Throwable t) {
            // Don't use logger here, as we may be shutting down the JVM and the logs won't be printed.
            t.printStackTrace();
            System.err.println("###############");
            System.err.println("Zuul Sample: initialization failed. Forcing shutdown now.");
            System.err.println("###############");
            exitCode = 1;
        }
        finally {
            // server shutdown
            if (server != null) {
                server.stop();
            }

            System.exit(exitCode);
        }
    }
}
