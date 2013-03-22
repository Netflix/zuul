package com.netflix.zuul.stats;

import com.netflix.zuul.context.RequestContext;

import com.netflix.zuul.stats.monitoring.MonitorRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/23/12
 * Time: 4:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class ErrorStatsManager {
    ConcurrentHashMap<String, ConcurrentHashMap<String, ErrorStatsData>> routeMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, ErrorStatsData>>();
    final static ErrorStatsManager INSTANCE = new ErrorStatsManager();

    public static ErrorStatsManager getManager() {
        return INSTANCE;
    }


    public ErrorStatsData getStats(String route, String cause) {
        Map<String, ErrorStatsData> map = routeMap.get(route);
        if (map == null) return null;
        return map.get(cause);
    }

    public void putStats(String route, String cause) {
        if (route == null) route = RequestContext.getCurrentContext().getRequest().getRequestURI();
        if (route == null) route = "UNKNOWN_ROUTE";
        route = route.replace("/", "_");
        ConcurrentHashMap<String, ErrorStatsData> statsMap = routeMap.get(route);
        if (statsMap == null) {
            statsMap = new ConcurrentHashMap<String, ErrorStatsData>();
            routeMap.putIfAbsent(route, statsMap);
        }
        ErrorStatsData sd = statsMap.get(cause);
        if (sd == null) {
            sd = new ErrorStatsData(route, cause);
            ErrorStatsData sd1 = statsMap.putIfAbsent(cause, sd);
            if (sd1 != null) {
                sd = sd1;
            } else {
                MonitorRegistry.getInstance().registerObject(sd);
            }
        }
        sd.update();
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Test
        public void testPutStats() {
            ErrorStatsManager sm = ErrorStatsManager.getManager();
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
            ErrorStatsManager sm = ErrorStatsManager.getManager();
            assertNotNull(sm);
            sm.putStats("test", "cause");
            assertNotNull(sm.getStats("test", "cause"));
        }

    }


}

