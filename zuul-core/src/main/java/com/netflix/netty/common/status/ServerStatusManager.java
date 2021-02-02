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

package com.netflix.netty.common.status;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * User: michaels@netflix.com
 * Date: 7/6/17
 * Time: 3:37 PM
 */
@Singleton
public class ServerStatusManager
{
    private final ApplicationInfoManager applicationInfoManager;

    @Inject
    public ServerStatusManager(ApplicationInfoManager applicationInfoManager) {
        this.applicationInfoManager = applicationInfoManager;
    }

    public void localStatus(InstanceInfo.InstanceStatus status) {
        applicationInfoManager.setInstanceStatus(status);
    }
}
