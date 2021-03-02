/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul.discovery;

import com.netflix.loadbalancer.Server.MetaInfo;

/**
 * @author Argha C
 * @since 2/25/21
 *
 * placeholder to mimic metainfo for a non-Eureka enabled server.
 * This exists to preserve compatibility with some current logic, but should be revisited.
 */
public final class SimpleMetaInfo {

    private final MetaInfo metaInfo;

    public SimpleMetaInfo(MetaInfo metaInfo) {
        this.metaInfo = metaInfo;
    }

    public String getServerGroup() {
        return metaInfo.getServerGroup();
    }

    public String getServiceIdForDiscovery() {
        return metaInfo.getServiceIdForDiscovery();
    }

    public String getInstanceId() {
        return metaInfo.getInstanceId();
    }
}
