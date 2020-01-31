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
package com.netflix.zuul;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.discovery.EurekaClient;
import com.netflix.netty.common.status.EurekaServerStatusManager;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.OutOfServiceConnectionsShutdown;
import com.netflix.zuul.origins.DiscoveryEnabledNettyOriginManager;
import com.netflix.zuul.origins.OriginManager;
import io.netty.util.concurrent.GlobalEventExecutor;

public class ZuulEurekaDiscoveryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(OriginManager.class).to(DiscoveryEnabledNettyOriginManager.class);

        bind(OutOfServiceConnectionsShutdown.class).toProvider(OutOfServiceConnectionsShutdownProvider.class).asEagerSingleton();;

        bind(EurekaServerStatusManager.class);
        bind(ServerStatusManager.class).to(EurekaServerStatusManager.class); // health/discovery status
    }

    static class OutOfServiceConnectionsShutdownProvider implements Provider<OutOfServiceConnectionsShutdown> {

        private final EurekaClient eurekaClient;
        private final BaseServerStartup baseServerStartup;

        @Inject
        public OutOfServiceConnectionsShutdownProvider(EurekaClient eurekaClient, BaseServerStartup baseServerStartup) {
            this.eurekaClient = eurekaClient;
            this.baseServerStartup = baseServerStartup;
        }

        @Override
        public OutOfServiceConnectionsShutdown get() {
            return new OutOfServiceConnectionsShutdown(eurekaClient, GlobalEventExecutor.INSTANCE,
                    baseServerStartup.server());
        }
    }

}
