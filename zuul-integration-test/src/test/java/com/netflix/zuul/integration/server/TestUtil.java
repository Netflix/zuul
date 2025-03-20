/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.integration.server;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public class TestUtil {
    private TestUtil() {}

    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static final String COMPRESSIBLE_CONTENT = "Hello Hello Hello Hello Hello";
    public static final String COMPRESSIBLE_CONTENT_TYPE = "text/plain";
    public static final String JUMBO_RESPONSE_BODY = StringUtils.repeat("abc", 1_000_000);

    public static DiscoveryEnabledServer makeDiscoveryEnabledServer(String appName, String ipAddress, int port) {
        InstanceInfo instanceInfo = new InstanceInfo(
                UUID.randomUUID().toString(),
                appName,
                appName,
                ipAddress,
                "sid123",
                new InstanceInfo.PortWrapper(true, port),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new DiscoveryEnabledServer(instanceInfo, false, true);
    }
}
