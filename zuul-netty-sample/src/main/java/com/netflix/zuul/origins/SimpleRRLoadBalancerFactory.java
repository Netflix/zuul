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

import com.netflix.config.DynamicStringMapProperty;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Format of the zuul.loadbalancer.simplerr.registry property is a comma-delimited list of origin name to host&port lists:
 *      {origin-name}={hostname}:{port};{hostname}:{port};{hostname}:{port} ...
 *
 * eg. "origin1=host1:8081;host2:8082,origin2=host3:8083"
 *
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 11:00 AM
 */
@Singleton
public class SimpleRRLoadBalancerFactory implements LoadBalancerFactory
{
    private static final Logger LOG = LoggerFactory.getLogger(SimpleRRLoadBalancerFactory.class);

    private ServerRegistry registry;

    public SimpleRRLoadBalancerFactory()
    {
        this.registry = new ServerRegistry();
    }

    @Override
    public LoadBalancer create(String name)
    {
        return new SimpleRRLoadBalancer(this.registry.get(name));
    }


    static class ServerRegistry
    {
        private final DynamicStringMapProperty REGISTRY_PROP = new DynamicStringMapProperty("zuul.loadbalancer.simplerr.registry", "");


        private AtomicReference<Map<String, List<ServerInfo>>> registry = new AtomicReference<>();

        public ServerRegistry()
        {
            registry.set(new HashMap<>());
            initFromProperty(REGISTRY_PROP);
            REGISTRY_PROP.addCallback(() -> initFromProperty(REGISTRY_PROP));
        }

        public ServerRegistry(Map<String, List<ServerInfo>> map)
        {
            this.registry.set(map);
        }

        private void initFromProperty(DynamicStringMapProperty property)
        {
            Map<String, String> map = property.getMap();
            Map<String, List<ServerInfo>> newRegistry = new HashMap<>();

            for (String name : map.keySet())
            {
                String[] hostAndPorts = map.get(name).split(";");
                List<ServerInfo> servers = new ArrayList<>();
                for (String hostAndPort : hostAndPorts) {
                    String[] split = hostAndPort.split(":");
                    if (split.length != 2) {
                        LOG.error("Each service should be in format <host>:<port>! hostAndPort=" + hostAndPort);
                    }
                    servers.add(new ServerInfo(split[0], Integer.parseInt(split[1])));
                }
                newRegistry.put(name, servers);
            }

            this.registry.set(newRegistry);
        }

        public List<ServerInfo> get(String name)
        {
            return registry.get().get(name);
        }
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
        @Test
        public void testInitFromProperty()
        {
            ServerRegistry registry = new ServerRegistry(new HashMap<>());
            DynamicStringMapProperty prop = new DynamicStringMapProperty("test.registry",
                    "origin1=host1:8081;host2:8082,origin2=host3:8083,origin3=");
            registry.initFromProperty(prop);

            assertEquals(Arrays.asList(new ServerInfo("host1", 8081), new ServerInfo("host2", 8082)),
                    registry.get("origin1"));
            assertEquals(Arrays.asList(new ServerInfo("host3", 8083)), registry.get("origin2"));
            assertNull(registry.get("origin3"));

//            assertEquals(new ServerInfo("host1", 8081), registry.get("origin1").get(0));
//            assertEquals(new ServerInfo("host2", 8082), registry.get("origin1").get(1));
        }
    }
}
