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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link VipUtils}.
 */
@RunWith(JUnit4.class)
public class VipUtilsTest {
    @Test(expected = NullPointerException.class)
    public void testGetVIPPrefix() {
        assertEquals("api-test", VipUtils.getVIPPrefix("api-test.netflix.net:7001"));
        assertEquals("api-test", VipUtils.getVIPPrefix("api-test.netflix.net"));
        assertEquals("api-test", VipUtils.getVIPPrefix("api-test:7001"));
        assertEquals("api-test", VipUtils.getVIPPrefix("api-test"));
        assertEquals("", VipUtils.getVIPPrefix(""));
        VipUtils.getVIPPrefix(null);
    }

    @Test(expected = NullPointerException.class)
    public void testExtractAppNameFromVIP() {
        assertEquals("api", VipUtils.extractAppNameFromVIP("api-test.netflix.net:7001"));
        assertEquals("api", VipUtils.extractAppNameFromVIP("api-test-blah.netflix.net:7001"));
        assertEquals("api", VipUtils.extractAppNameFromVIP("api"));
        assertEquals("", VipUtils.extractAppNameFromVIP(""));
        VipUtils.extractAppNameFromVIP(null);
    }
}
