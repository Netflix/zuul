package com.netflix.zuul.dependency.httpclient.hystrix;

import org.junit.Assert;
import org.junit.Test;


public class HostCommandTest {
    @Test
    public void testConstruction() {
        HostCommand hc = new HostCommand(null, null, null);
        Assert.assertEquals("default", hc.getCommandGroup().name());
        Assert.assertEquals(HostCommand.class.getSimpleName(), hc.getCommandKey().name());
    }

    @Test
    public void testConstructionWithCommandKey() {
        HostCommand hc = new HostCommand("myCommand", null, null, null);
        Assert.assertEquals("myCommand", hc.getCommandGroup().name());
        Assert.assertEquals(HostCommand.class.getSimpleName(), hc.getCommandKey().name());
    }

    @Test
    public void testConstructionWithGroupKeyAndCommandKey() {
        HostCommand hc = new HostCommand("myGroup", "myCommand", null, null, null);
        Assert.assertEquals("myGroup", hc.getCommandGroup().name());
        Assert.assertEquals("myCommand", hc.getCommandKey().name());
    }
}