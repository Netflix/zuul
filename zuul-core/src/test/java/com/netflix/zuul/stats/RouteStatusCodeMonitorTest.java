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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RouteStatusCodeMonitor}.
 */
class RouteStatusCodeMonitorTest {
    @Test
    void testUpdateStats() {
        RouteStatusCodeMonitor sd = new RouteStatusCodeMonitor("test", 200);
        assertThat(sd.route).isEqualTo("test");
        sd.update();
        assertThat(sd.getCount()).isEqualTo(1);
        sd.update();
        assertThat(sd.getCount()).isEqualTo(2);
    }

    @Test
    void testEquals() {
        RouteStatusCodeMonitor sd = new RouteStatusCodeMonitor("test", 200);
        RouteStatusCodeMonitor sd1 = new RouteStatusCodeMonitor("test", 200);
        RouteStatusCodeMonitor sd2 = new RouteStatusCodeMonitor("test1", 200);
        RouteStatusCodeMonitor sd3 = new RouteStatusCodeMonitor("test", 201);

        assertThat(sd1).isEqualTo(sd);
        assertThat(sd).isEqualTo(sd1);
        assertThat(sd).isNotEqualTo(sd2);
        assertThat(sd).isNotEqualTo(sd3);
        assertThat(sd2).isNotEqualTo(sd3);
    }
}
