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

import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.inject.Singleton;

@Singleton
public class DiscoveryServerIpAddrExtractor implements ServerIpAddrExtractor {

    @Nullable
    @Override
    public String getIpAddrFromServer(Server server) {
        if (server instanceof DiscoveryEnabledServer) {
            DiscoveryEnabledServer discoveryServer = (DiscoveryEnabledServer) server;
            if (discoveryServer.getInstanceInfo() != null) {
                String ip = discoveryServer.getInstanceInfo().getIPAddr();
                if (StringUtils.isNotBlank(ip)) {
                    return ip;
                }
            }
        }

        return null;
    }

}
