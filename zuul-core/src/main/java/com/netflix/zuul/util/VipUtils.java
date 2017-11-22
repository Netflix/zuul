/*
 * Copyright 2018 Netflix, Inc.
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VipUtils
{
    public static String getVIPPrefix(String vipAddress)
    {
        String vipHost = vipAddress.split(":")[0];
        return vipHost.split("\\.")[0];
    }

    public static String extractAppNameFromVIP(String vipAddress)
    {
        String vipPrefix = getVIPPrefix(vipAddress);
        return vipPrefix.split("-")[0];
    }

    public static class UnitTest
    {
        @Test(expected = NullPointerException.class)
        public void testGetVIPPrefix()
        {
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test.netflix.net:7001"));
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test.netflix.net"));
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test:7001"));
            assertEquals("api-test", VipUtils.getVIPPrefix("api-test"));
            assertEquals("", VipUtils.getVIPPrefix(""));
            VipUtils.getVIPPrefix(null);
        }

        @Test(expected = NullPointerException.class)
        public void testExtractAppNameFromVIP()
        {
            assertEquals("api", VipUtils.extractAppNameFromVIP("api-test.netflix.net:7001"));
            assertEquals("api", VipUtils.extractAppNameFromVIP("api-test-blah.netflix.net:7001"));
            assertEquals("api", VipUtils.extractAppNameFromVIP("api"));
            assertEquals("", VipUtils.extractAppNameFromVIP(""));
            VipUtils.extractAppNameFromVIP(null);
        }
    }
}
