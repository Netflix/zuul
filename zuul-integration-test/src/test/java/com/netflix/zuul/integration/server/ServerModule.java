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

import com.google.inject.AbstractModule;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.*;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.context.ZuulSessionContextDecorator;
import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.MutableFilterRegistry;
import com.netflix.zuul.init.ZuulFiltersModule;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.stats.BasicRequestMetricsPublisher;
import com.netflix.zuul.stats.RequestMetricsPublisher;
import org.apache.commons.configuration.AbstractConfiguration;

import java.io.FilenameFilter;

public class ServerModule extends AbstractModule {
    @Override
    protected void configure() {
        try {
          ConfigurationManager.loadCascadedPropertiesFromResources("application");
        } catch (Exception ex) {
          throw new RuntimeException("Error loading configuration: " + ex.getMessage(), ex);
        }

        bind(FilenameFilter.class).to(NoFilenameFilter.class);

        bind(AbstractConfiguration.class).toInstance(ConfigurationManager.getConfigInstance());

        install(new EurekaModule());

        // sample specific bindings
        bind(BaseServerStartup.class).to(ServerStartup.class);

        // use provided basic netty origin manager
        bind(OriginManager.class).to(BasicNettyOriginManager.class);

        // zuul filter loading
        install(new ZuulFiltersModule());
        bind(FilterLoader.class).toProvider(FilterLoaderProvider.class);
        bind(FilterRegistry.class).to(MutableFilterRegistry.class);

        // general server bindings
        bind(ServerStatusManager.class); // health/discovery status
        bind(SessionContextDecorator.class).to(ZuulSessionContextDecorator.class); // decorate new sessions when requests come in
        bind(Registry.class).to(DefaultRegistry.class); // atlas metrics registry
        bind(RequestCompleteHandler.class).to(BasicRequestCompleteHandler.class); // metrics post-request completion
        bind(RequestMetricsPublisher.class).to(BasicRequestMetricsPublisher.class); // timings publisher

        // access logger, including request ID generator
        bind(AccessLogPublisher.class).toInstance(new AccessLogPublisher("ACCESS",
                (channel, httpRequest) -> ClientRequestReceiver.getRequestFromChannel(channel).getContext().getUUID()));
    }
}
