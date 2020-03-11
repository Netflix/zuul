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
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UNKNOWN;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;

/**
 * User: michaels@netflix.com
 * Date: 7/6/17
 * Time: 3:37 PM
 */
@Singleton
public class EurekaServerStatusManager implements ServerStatusManager {
    private final ApplicationInfoManager applicationInfoManager;
    private final DiscoveryClient discoveryClient;

    @Inject
    public EurekaServerStatusManager(ApplicationInfoManager applicationInfoManager, DiscoveryClient discoveryClient) {
        super();
        this.applicationInfoManager = applicationInfoManager;
        this.discoveryClient = discoveryClient;
    }

    public InstanceStatus status() {

        // NOTE: when debugging this locally, found to my surprise that when the instance is maked OUT_OF_SERVICE remotely
        // in Discovery, although the StatusChangeEvent does get fired, the _local_ InstanceStatus (ie.
        // applicationInfoManager.getInfo().getStatus()) does not get changed to reflect that.
        // So that's why I'm doing this little dance here of looking at both remote and local statuses.

        InstanceStatus local = localStatus();
        InstanceStatus remote = remoteStatus();

        if (local == UP && remote != UNKNOWN) {
            return remote;
        }
        else {
            return local;
        }
    }

    public InstanceStatus localStatus() {
        return applicationInfoManager.getInfo().getStatus();
    }

    public InstanceStatus remoteStatus() {
        return discoveryClient.getInstanceRemoteStatus();
    }

    @Override
    public void localStatus(ServerStatus status) {
        InstanceStatus instanceStatus = status == ServerStatus.UP ? InstanceStatus.UP : InstanceStatus.DOWN;
        applicationInfoManager.setInstanceStatus(instanceStatus);
    }

    public int health() {
        // TODO
        throw new UnsupportedOperationException();
    }
}
