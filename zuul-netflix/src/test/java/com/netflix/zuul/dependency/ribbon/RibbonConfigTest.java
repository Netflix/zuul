package com.netflix.zuul.dependency.ribbon;

import org.junit.Test;

public class RibbonConfigTest {
    @Test
    public void defaultVipAddressForStandardStack() {
        //todo fix
//      assertEquals("null-prod.netflix.net:7001", RibbonConfig.getDefaultVipAddress("prod"));
    }


    @Test
    public void defaultVipAddressForLatAmStack() {
        //todo fix
//      assertEquals("null-prod.latam.netflix.net:7001", RibbonConfig.getDefaultVipAddress("prod.latam"));
    }
}