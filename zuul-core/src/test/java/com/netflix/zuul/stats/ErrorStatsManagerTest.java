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
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link ErrorStatsManager}.
 */
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
