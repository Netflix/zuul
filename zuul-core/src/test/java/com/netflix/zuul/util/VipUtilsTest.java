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

package com.netflix.zuul.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VipUtils}.
 */
class VipUtilsTest {
    @Test
    void testGetVIPPrefix() {
        assertThrows(NullPointerException.class, () -> {
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test.netflix.net:7001"));
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test.netflix.net"));
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test:7001"));
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test"));
            assertEquals("", VipUtils.getVIPPrefix(""));
            VipUtils.getVIPPrefix(null);
        });
    }

    @Test
    void testExtractAppNameFromVIP() {
        assertThrows(NullPointerException.class, () -> {
            assertEquals("api", VipUtils.extractUntrustedAppNameFromVIP("api-test.netflix.net:7001"));
            assertEquals("api", VipUtils.extractUntrustedAppNameFromVIP("api-test-blah.netflix.net:7001"));
            assertEquals("api", VipUtils.extractUntrustedAppNameFromVIP("api"));
            assertEquals("", VipUtils.extractUntrustedAppNameFromVIP(""));
            VipUtils.extractUntrustedAppNameFromVIP(null);
        });
    }
}
