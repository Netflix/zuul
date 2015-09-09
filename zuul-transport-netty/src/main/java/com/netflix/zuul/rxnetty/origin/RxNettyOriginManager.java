/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.zuul.rxnetty.origin;

import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RxNettyOriginManager implements OriginManager {

    private final Map<String, RxNettyOrigin> vipsVsOrigin = new ConcurrentHashMap<>();

    public RxNettyOriginManager(String[] vips, HostSourceFactory hostSourceFactory) {
        for (String vip : vips) {
            vipsVsOrigin.put(vip, newOrigin(vip, hostSourceFactory));
        }
    }

    @Override
    public Origin getOrigin(String name) {
        return vipsVsOrigin.get(name);
    }

    protected RxNettyOrigin newOrigin(String vip, HostSourceFactory hostSourceFactory) {
        return new RxNettyOrigin(vip, hostSourceFactory.call(vip));
    }
}
