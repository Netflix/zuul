package com.netflix.zuul.stats;

import com.netflix.zuul.stats.monitoring.NamedCount;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/23/12
 * Time: 4:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class ErrorStatsData implements NamedCount
{

    String id;

    String error_cause;

    AtomicLong count = new AtomicLong();


    public ErrorStatsData(String route, String cause) {
        id = route + "_" + cause;
        this.error_cause = cause;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ErrorStatsData that = (ErrorStatsData) o;

        return !(error_cause != null ? !error_cause.equals(that.error_cause) : that.error_cause != null);

    }

    @Override
    public int hashCode() {
        return error_cause != null ? error_cause.hashCode() : 0;
    }

    public void update() {
        count.incrementAndGet();
    }

    @Override
    public String getName() {
        return id;
    }

    @Override
    public long  getCount() {
        return count.get();
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Test
        public void testUpdateStats() {
            ErrorStatsData sd = new ErrorStatsData("route", "test");
            assertEquals(sd.error_cause, "test");
            sd.update();
            assertEquals(sd.count.get(), 1);
            sd.update();
            assertEquals(sd.count.get(), 2);
        }


        @Test
        public void testEquals() {
            ErrorStatsData sd = new ErrorStatsData("route", "test");
            ErrorStatsData sd1 = new ErrorStatsData("route", "test");
            ErrorStatsData sd2 = new ErrorStatsData("route", "test1");
            ErrorStatsData sd3 = new ErrorStatsData("route", "test");

            assertTrue(sd.equals(sd1));
            assertTrue(sd1.equals(sd));
            assertTrue(sd.equals(sd));
            assertFalse(sd.equals(sd2));
            assertFalse(sd2.equals(sd3));
        }
    }

}
