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

package com.netflix.netty.common.metrics;

import static org.junit.Assert.assertEquals;

import io.netty.buffer.ByteBuf;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InstrumentedResourceLeakDetectorTest {

    InstrumentedResourceLeakDetector<Object> leakDetector;

    @Before
    public void setup() {
        leakDetector = new InstrumentedResourceLeakDetector<>(ByteBuf.class, 1);
    }

    @Test
    public void test() {
        leakDetector.reportTracedLeak("test", "test");
        assertEquals(leakDetector.leakCounter.get(), 1);

        leakDetector.reportTracedLeak("test", "test");
        assertEquals(leakDetector.leakCounter.get(), 2);

        leakDetector.reportTracedLeak("test", "test");
        assertEquals(leakDetector.leakCounter.get(), 3);
    }
}
