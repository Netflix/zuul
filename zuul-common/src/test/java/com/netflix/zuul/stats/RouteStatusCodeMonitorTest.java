package com.netflix.zuul.stats;

import org.junit.Test;

import static org.junit.Assert.*;

public class RouteStatusCodeMonitorTest {

    @Test
    public void testUpdateStats() {
        RouteStatusCodeMonitor sd = new RouteStatusCodeMonitor("test", 200);
        assertEquals(sd.route, "test");
        sd.update();
        assertEquals(sd.getCount(), 1);
        sd.update();
        assertEquals(sd.getCount(), 2);
    }


    @Test
    public void testEquals() {
        RouteStatusCodeMonitor sd = new RouteStatusCodeMonitor("test", 200);
        RouteStatusCodeMonitor sd1 = new RouteStatusCodeMonitor("test", 200);
        RouteStatusCodeMonitor sd2 = new RouteStatusCodeMonitor("test1", 200);
        RouteStatusCodeMonitor sd3 = new RouteStatusCodeMonitor("test", 201);

        assertTrue(sd.equals(sd1));
        assertTrue(sd1.equals(sd));
        assertTrue(sd.equals(sd));
        assertFalse(sd.equals(sd2));
        assertFalse(sd.equals(sd3));
        assertFalse(sd2.equals(sd3));
    }
}