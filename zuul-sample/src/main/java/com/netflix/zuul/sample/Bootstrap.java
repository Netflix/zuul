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

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.zuul.BasicFilterUsageNotifier;
import com.netflix.zuul.BasicRequestCompleteHandler;
import com.netflix.zuul.DefaultFilterFactory;
import com.netflix.zuul.FilterFactory;
import com.netflix.zuul.StaticFilterLoader;
import com.netflix.zuul.context.ZuulSessionContextDecorator;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.netty.server.DirectMemoryMonitor;
import com.netflix.zuul.netty.server.Server;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import com.netflix.zuul.sample.filters.Debug;
import com.netflix.zuul.sample.filters.endpoint.Healthcheck;
import com.netflix.zuul.sample.filters.inbound.DebugRequest;
import com.netflix.zuul.sample.filters.inbound.Routes;
import com.netflix.zuul.sample.filters.inbound.SampleServiceFilter;
import com.netflix.zuul.sample.filters.outbound.ZuulResponseFilter;
import com.netflix.zuul.sample.push.SamplePushMessageSenderInitializer;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap
 * <p>
 * Author: Arthur Gonigberg
 * Date: November 20, 2017
 */
public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private static final Set<? extends Class<? extends ZuulFilter<?, ?>>> FILTER_TYPES;

    static {
        Set<Class<? extends ZuulFilter<?, ?>>> classes = new LinkedHashSet<>();
        classes.add(Healthcheck.class);
        classes.add(Debug.class);
        classes.add(Routes.class);
        classes.add(SampleServiceFilter.class);
        classes.add(ZuulResponseFilter.class);
        classes.add(DebugRequest.class);

        FILTER_TYPES = Collections.unmodifiableSet(classes);
    }

    public static void main(String[] args) {
        new Bootstrap().start();
    }

    public void start() {
        long startNanos = System.nanoTime();
        logger.info("Zuul Sample: starting up.");
        int exitCode = 0;

        Server server = null;

        try {
            ConfigurationManager.loadCascadedPropertiesFromResources("application");

            AccessLogPublisher accessLogPublisher = new AccessLogPublisher(
                    "ACCESS", (channel, httpRequest) -> ClientRequestReceiver.getRequestFromChannel(channel)
                            .getContext()
                            .getUUID());

            ApplicationInfoManager instance = new ApplicationInfoManager(null, null, null);

            PushConnectionRegistry pushConnectionRegistry = new PushConnectionRegistry();
            SamplePushMessageSenderInitializer pushMessageSenderInitializer =
                    new SamplePushMessageSenderInitializer(pushConnectionRegistry);
            DefaultRegistry registry = new DefaultRegistry();
            SampleServerStartup serverStartup = new SampleServerStartup(
                    new ServerStatusManager(instance) {
                        @Override
                        public void localStatus(InstanceStatus status) {}
                    },
                    new StaticFilterLoader(new SampleFilterFactory(), FILTER_TYPES),
                    new ZuulSessionContextDecorator(new BasicNettyOriginManager(registry)),
                    new BasicFilterUsageNotifier(registry),
                    new BasicRequestCompleteHandler(),
                    registry,
                    new DirectMemoryMonitor(registry),
                    new EventLoopGroupMetrics(registry),
                    null,
                    instance,
                    accessLogPublisher,
                    pushConnectionRegistry,
                    pushMessageSenderInitializer);
            serverStartup.init();
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
        } finally {
            // server shutdown
            if (server != null) {
                server.stop();
            }

            System.exit(exitCode);
        }
    }

    private static class SampleFilterFactory implements FilterFactory {

        private final DefaultFilterFactory filterFactory;

        public SampleFilterFactory() {
            filterFactory = new DefaultFilterFactory();
        }

        @Override
        public ZuulFilter<?, ?> newInstance(Class<?> clazz)
                throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException {
            if (clazz.equals(SampleServiceFilter.class)) {
                return new SampleServiceFilter(new SampleService());
            } else {
                return filterFactory.newInstance(clazz);
            }
        }
    }
}
