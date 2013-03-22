package com.netflix.zuul.stats;

import com.netflix.zuul.stats.monitoring.NamedCount;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicLong;


import static org.junit.Assert.*;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/3/12
 * Time: 3:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class RouteStatusCodeMonitor implements NamedCount {
    String route_code;

    String route;
    int status_code;


    private final AtomicLong count = new AtomicLong();


    public RouteStatusCodeMonitor(String route, int status_code) {
        this.route = route;
        this.status_code = status_code;
        route_code = route + "_" + status_code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RouteStatusCodeMonitor statsData = (RouteStatusCodeMonitor) o;

        if (status_code != statsData.status_code) return false;
        if (route != null ? !route.equals(statsData.route) : statsData.route != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = route != null ? route.hashCode() : 0;
        result = 31 * result + status_code;
        return result;
    }

    @Override
    public String getName() {
        return route_code;
    }

    public long getCount() {
        return count.get();
    }

    public void update() {
        count.incrementAndGet();
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Test
        public void testUpdateStats() {
            RouteStatusCodeMonitor sd = new RouteStatusCodeMonitor("test", 200);
            assertEquals(sd.route, "test");
            sd.update();
            assertEquals(sd.count.get(), 1);
            sd.update();
            assertEquals(sd.count.get(), 2);
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

}
