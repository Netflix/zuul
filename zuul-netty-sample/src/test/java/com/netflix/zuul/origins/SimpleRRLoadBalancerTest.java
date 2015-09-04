package com.netflix.zuul.origins;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SimpleRRLoadBalancerTest {
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
        List<ServerInfo> serverList = Collections.singletonList(new ServerInfo("host1", 8081));
        SimpleRRLoadBalancer lb = new SimpleRRLoadBalancer(serverList);

        assertEquals(serverList.get(0), lb.getNextServer());
        assertEquals(serverList.get(0), lb.getNextServer());
        assertEquals(serverList.get(0), lb.getNextServer());
    }
}