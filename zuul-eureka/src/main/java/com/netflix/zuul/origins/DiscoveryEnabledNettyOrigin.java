/**
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.origins;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.niws.RequestAttempt;

public class DiscoveryEnabledNettyOrigin extends BasicNettyOrigin {

    public DiscoveryEnabledNettyOrigin(String name, String vip, Registry registry) {
        super(name, vip, new DiscoveryServerIpAddrExtractor(), registry);
    }

    @Override
    public RequestAttempt newRequestAttempt(Server server, SessionContext zuulCtx, int attemptNum) {
        RequestAttempt requestAttempt = super.newRequestAttempt(server, zuulCtx, attemptNum);
        if (server instanceof DiscoveryEnabledServer) {
            InstanceInfo instanceInfo = ((DiscoveryEnabledServer) server).getInstanceInfo();
            requestAttempt.setApp(instanceInfo.getAppName().toLowerCase());
            requestAttempt.setAsg(instanceInfo.getASGName());
            requestAttempt.setInstanceId(instanceInfo.getInstanceId());
            requestAttempt.setHost(instanceInfo.getHostName());
            requestAttempt.setPort(instanceInfo.getPort());

            if (server.getPort() == instanceInfo.getSecurePort()) {
                requestAttempt.setVip(instanceInfo.getSecureVipAddress());
            } else {
                requestAttempt.setVip(instanceInfo.getVIPAddress());
            }

            if (instanceInfo.getDataCenterInfo() instanceof AmazonInfo) {
                String availabilityZone = ((AmazonInfo) instanceInfo.getDataCenterInfo()).getMetadata().get("availability-zone");
                requestAttempt.setAvailabilityZone(availabilityZone);
            }
        }

        return requestAttempt;
    }

}
