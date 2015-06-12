package com.netflix.zuul.stats;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class ErrorStatsManagerTest {
    @Test
    public void testPutStats() {
        ErrorStatsManager sm = new ErrorStatsManager();
        assertNotNull(sm);
        sm.putStats("test", "cause");
        assertNotNull(sm.routeMap.get("test"));
        ConcurrentHashMap<String, ErrorStatsData> map = sm.routeMap.get("test");
        ErrorStatsData sd = map.get("cause");
        assertEquals(sd.count.get(), 1);
        sm.putStats("test", "cause");
        assertEquals(sd.count.get(), 2);
    }


    @Test
    public void testGetStats() {
        ErrorStatsManager sm = new ErrorStatsManager();
        assertNotNull(sm);
        sm.putStats("test", "cause");
        assertNotNull(sm.getStats("test", "cause"));
    }
}