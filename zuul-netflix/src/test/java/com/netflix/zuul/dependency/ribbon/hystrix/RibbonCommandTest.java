package com.netflix.zuul.dependency.ribbon.hystrix;

import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;

public class RibbonCommandTest {
    private static final String localhost = "http://localhost";

    @Test
    public void testConstruction() throws URISyntaxException {
        RibbonCommand rc = new RibbonCommand(null, null, localhost, null, null, null);
        Assert.assertEquals("default", rc.getCommandGroup().name());
        Assert.assertEquals(RibbonCommand.class.getSimpleName(), rc.getCommandKey().name());
    }

    @Test
    public void testConstructionWithCommandKey() throws URISyntaxException {
        RibbonCommand rc = new RibbonCommand("myCommand", null, null, localhost, null, null, null);
        Assert.assertEquals("myCommand", rc.getCommandGroup().name());
        Assert.assertEquals(RibbonCommand.class.getSimpleName(), rc.getCommandKey().name());
    }

    @Test
    public void testConstructionWithGroupKeyAndCommandKey() throws URISyntaxException {
        RibbonCommand rc = new RibbonCommand("myGroup", "myCommand", null, null, localhost, null, null, null);
        Assert.assertEquals("myGroup", rc.getCommandGroup().name());
        Assert.assertEquals("myCommand", rc.getCommandKey().name());
    }
}