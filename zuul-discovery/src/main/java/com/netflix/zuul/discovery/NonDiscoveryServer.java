/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul.discovery;

import com.netflix.loadbalancer.Server;

/**
 * @author Argha C
 * @since 3/1/21
 * <p>
 * This exists merely to wrap a resolver lookup result, that is not discovery enabled.
 */
public class NonDiscoveryServer implements ResolverResult {

    private final Server server;

    public NonDiscoveryServer(String host, int port) {
        this.server = new Server(host, port);
    }

    @Override
    public String getHost() {
        return server.getHost();
    }

    @Override
    public int getPort() {
        return server.getPort();
    }

    @Override
    public boolean isDiscoveryEnabled() {
        return false;
    }
}
