/**
 * Copyright 2015 Netflix, Inc.
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 11:00 AM
 */
public class SimpleRRLoadBalancer implements LoadBalancer
{
    private final List<ServerInfo> servers;
    private final AtomicInteger serverIndex = new AtomicInteger(0);

    public SimpleRRLoadBalancer(List<ServerInfo> servers)
    {
        if (servers == null) {
            throw new NullPointerException("Null server list passed to LoadBalancer!");
        }
        if (servers.size() == 0) {
            throw new IllegalArgumentException("Empty server list passed to LoadBalancer!");
        }
        this.servers = servers;
    }

    @Override
    public void init()
    {
    }

    @Override
    public ServerInfo getNextServer()
    {
        int index = serverIndex.getAndIncrement();
        if (index < servers.size()) {
            return servers.get(index);
        }
        else {
            // Hit last server in list, so reset the index.
            serverIndex.set(0);
            return getNextServer();
        }
    }
}
