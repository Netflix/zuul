package com.netflix.zuul.origins;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

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

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest
    {
        @Test
        public void testGetNextServer()
        {
            List<ServerInfo> serverList = Arrays.asList(new ServerInfo("host1", 8081), new ServerInfo("host2", 8082));
            SimpleRRLoadBalancer lb = new SimpleRRLoadBalancer(serverList);

            assertEquals(serverList.get(0), lb.getNextServer());
            assertEquals(serverList.get(1), lb.getNextServer());
            assertEquals(serverList.get(0), lb.getNextServer());
            assertEquals(serverList.get(1), lb.getNextServer());
            assertEquals(serverList.get(0), lb.getNextServer());
            assertEquals(serverList.get(1), lb.getNextServer());
        }

        @Test
        public void testGetNextServer_SingleServer()
        {
            List<ServerInfo> serverList = Arrays.asList(new ServerInfo("host1", 8081));
            SimpleRRLoadBalancer lb = new SimpleRRLoadBalancer(serverList);

            assertEquals(serverList.get(0), lb.getNextServer());
            assertEquals(serverList.get(0), lb.getNextServer());
            assertEquals(serverList.get(0), lb.getNextServer());
        }
    }
}
