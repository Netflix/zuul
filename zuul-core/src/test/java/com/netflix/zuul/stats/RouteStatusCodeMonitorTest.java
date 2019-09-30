/*
 * Copyright 2019 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link RouteStatusCodeMonitor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class RouteStatusCodeMonitorTest {
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
