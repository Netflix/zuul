/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.stats;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.annotations.MonitorTags;
import com.netflix.servo.tag.BasicTag;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.Tag;
import com.netflix.servo.tag.TagList;
import com.netflix.zuul.stats.monitoring.NamedCount;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Implementation of a Named counter to monitor and count error causes by route. Route is a defined zuul concept to
 * categorize requests into buckets. By default this is the first segment of the uri
 * @author Mikey Cohen
 * Date: 2/23/12
 * Time: 4:16 PM
 */
public class ErrorStatsData implements NamedCount
{


    String id;

    @MonitorTags
    TagList tagList;

    String error_cause;

    @Monitor(name = "count", type = DataSourceType.COUNTER)
    AtomicLong count = new AtomicLong();

    /**
     * create a counter by route and cause of error
     * @param route
     * @param cause
     */
    public ErrorStatsData(String route, String cause) {
        if(null == route || "".equals(route)){
            route = "UNKNOWN";
        }
        id = route + "_" + cause;

        this.error_cause = cause;
        tagList = BasicTagList.of(new BasicTag("ID", id));



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

    /**
     * increments the counter
     */
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
