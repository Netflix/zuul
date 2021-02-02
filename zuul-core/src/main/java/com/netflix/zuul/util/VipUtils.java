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

public final class VipUtils
{
    public static String getVIPPrefix(String vipAddress) {
        for (int i = 0; i < vipAddress.length(); i++) {
            char c = vipAddress.charAt(i);
            if (c == '.' || c == ':') {
                return vipAddress.substring(0, i);
            }
        }
        return vipAddress;
    }

    /**
     * Use {@link #extractUntrustedAppNameFromVIP} instead.
     */
    @Deprecated
    public static String extractAppNameFromVIP(String vipAddress) {
        String vipPrefix = getVIPPrefix(vipAddress);
        return vipPrefix.split("-")[0];
    }

    /**
     * Attempts to derive an app name from the VIP.   Because the VIP is an arbitrary collection of characters, the
     * value is just a best guess and not suitable for security purposes.
     */
    public static String extractUntrustedAppNameFromVIP(String vipAddress) {
        for (int i = 0; i < vipAddress.length(); i++) {
            char c = vipAddress.charAt(i);
            if (c == '-' || c == '.' || c == ':') {
                return vipAddress.substring(0, i);
            }
        }
        return vipAddress;
    }

    private VipUtils() {}
}
