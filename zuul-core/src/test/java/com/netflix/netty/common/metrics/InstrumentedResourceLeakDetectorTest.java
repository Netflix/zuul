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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentedResourceLeakDetectorTest {

    InstrumentedResourceLeakDetector<Object> leakDetector;

    @BeforeEach
    void setup() {
        leakDetector = new InstrumentedResourceLeakDetector<>(ByteBuf.class, 1);
    }

    @Test
    void test() {
        leakDetector.reportTracedLeak("test", "test");
        assertEquals(1, leakDetector.leakCounter.get());

        leakDetector.reportTracedLeak("test", "test");
        assertEquals(2, leakDetector.leakCounter.get());

        leakDetector.reportTracedLeak("test", "test");
        assertEquals(3, leakDetector.leakCounter.get());
    }
}
