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
package com.netflix.zuul.origins;

import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContext;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveryEnabledNettyOriginManager implements OriginManager<DiscoveryEnabledNettyOrigin> {

    private final Registry registry;
    private final Map<String, DiscoveryEnabledNettyOrigin> originMappings;

    @Inject
    public DiscoveryEnabledNettyOriginManager(Registry registry) {
        this.registry = registry;
        this.originMappings = new ConcurrentHashMap<>();
    }

    @Override
    public DiscoveryEnabledNettyOrigin getOrigin(String name, String vip, String uri, SessionContext ctx) {
        return originMappings.computeIfAbsent(name, n -> createOrigin(name, vip, uri, false, ctx));
    }

    @Override
    public DiscoveryEnabledNettyOrigin createOrigin(String name, String vip, String uri, boolean useFullVipName, SessionContext ctx) {
        return new DiscoveryEnabledNettyOrigin(name, vip, registry);
    }

}
