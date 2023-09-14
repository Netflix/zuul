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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.netflix.loadbalancer.ConfigurationBasedServerList;
import com.netflix.loadbalancer.Server;

import java.util.List;

import static com.netflix.zuul.integration.server.TestUtil.makeDiscoveryEnabledServer;

public class OriginServerList extends ConfigurationBasedServerList {
    @Override
    protected List<Server> derive(String value) {
        List<Server> list = Lists.newArrayList();
        if (!Strings.isNullOrEmpty(value)) {
            for (String s : value.split(",")) {
                String[] hostAndPort = s.split(":");
                String host = hostAndPort[0];
                int port = Integer.parseInt(hostAndPort[1]);
                list.add(makeDiscoveryEnabledServer("", host, port));
            }
        }
        return list;
    }
}
