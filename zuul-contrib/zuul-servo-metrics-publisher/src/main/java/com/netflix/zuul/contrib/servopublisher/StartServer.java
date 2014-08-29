/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.contrib.servopublisher;

import com.netflix.zuul.filterstore.ClassPathFilterStore;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.ZuulServer;
import com.netflix.zuul.metrics.ZuulPlugins;

public class StartServer {
    static final int DEFAULT_PORT = 8001; // because eureka-client.properties defines this (donchya love non-local reasoning)

    public static void main(final String[] args) {
        FilterStore filterStore = new ClassPathFilterStore("com.netflix.zuul.filter");
        ZuulPlugins.getInstance().registerMetricsPublisher(ZuulServoMetricsPublisher.getInstance());
        ZuulServer.start(DEFAULT_PORT, filterStore);
    }
}
