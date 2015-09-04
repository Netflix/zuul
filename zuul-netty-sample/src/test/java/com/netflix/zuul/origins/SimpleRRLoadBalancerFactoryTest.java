package com.netflix.zuul.origins;

import com.netflix.config.DynamicStringMapProperty;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class SimpleRRLoadBalancerFactoryTest {
    @Test
    public void testInitFromProperty()
    {
        SimpleRRLoadBalancerFactory.ServerRegistry registry = new SimpleRRLoadBalancerFactory.ServerRegistry(new HashMap<>());
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