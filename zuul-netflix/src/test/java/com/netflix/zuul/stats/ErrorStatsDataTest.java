package com.netflix.zuul.stats;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class ErrorStatsDataTest {

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