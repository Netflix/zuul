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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ErrorStatsData}.
 */
@ExtendWith(MockitoExtension.class)
class ErrorStatsDataTest {

    @Test
    void testUpdateStats() {
        ErrorStatsData sd = new ErrorStatsData("route", "test");
        sd.update();
        assertEquals(1, sd.getCount());
        sd.update();
        assertEquals(2, sd.getCount());
    }

    @Test
    void testEquals() {
        ErrorStatsData sd = new ErrorStatsData("route", "test");
        ErrorStatsData sd1 = new ErrorStatsData("route", "test");
        ErrorStatsData sd2 = new ErrorStatsData("route", "test1");
        ErrorStatsData sd3 = new ErrorStatsData("route", "test");

        assertEquals(sd, sd1);
        assertEquals(sd1, sd);
        assertEquals(sd, sd);
        assertNotEquals(sd, sd2);
        assertNotEquals(sd2, sd3);
    }
}
