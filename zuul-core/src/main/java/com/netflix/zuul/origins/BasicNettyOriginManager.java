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

package com.netflix.zuul.origins;

import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic Netty Origin Manager that most apps can use. This can also serve as a useful template for creating more
 * complex origin managers.
 *
 * Author: Arthur Gonigberg
 * Date: November 30, 2017
 */
@Singleton
public class BasicNettyOriginManager implements OriginManager<BasicNettyOrigin> {

    private final Registry registry;
    private final ConcurrentHashMap<String, BasicNettyOrigin> originMappings;

    @Inject
    public BasicNettyOriginManager(Registry registry) {
        this.registry = registry;
        this.originMappings = new ConcurrentHashMap<>();
    }

    @Override
    public BasicNettyOrigin getOrigin(String name, String vip, String uri, SessionContext ctx) {
        return originMappings.computeIfAbsent(name, n -> createOrigin(name, vip, uri, false, ctx));
    }

    @Override
    public BasicNettyOrigin createOrigin(String name, String vip, String uri, boolean useFullVipName, SessionContext ctx) {
        return new BasicNettyOrigin(name, vip, registry);
    }
}
