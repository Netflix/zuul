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
package com.netflix.zuul.stats;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.annotations.MonitorTags;
import com.netflix.servo.tag.BasicTag;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.TagList;
import com.netflix.zuul.stats.monitoring.NamedCount;

import java.util.concurrent.atomic.AtomicLong;

/**
 * counter for per route/status code counting
 * @author Mikey Cohen
 * Date: 2/3/12
 * Time: 3:04 PM
 */
public class RouteStatusCodeMonitor implements NamedCount {

    @MonitorTags
    TagList tagList;


    String route_code;

    String route;
    int status_code;

    @Monitor(name = "count", type = DataSourceType.COUNTER)
    @VisibleForTesting
    final AtomicLong count = new AtomicLong();


    public RouteStatusCodeMonitor(String route, int status_code) {
        if(route == null) route = "";
        this.route = route;
        this.status_code = status_code;
        route_code = route + "_" + status_code;
        tagList = BasicTagList.of(new BasicTag("ID", route_code));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RouteStatusCodeMonitor statsData = (RouteStatusCodeMonitor) o;

        if (status_code != statsData.status_code) return false;
        if (route != null ? !route.equals(statsData.route) : statsData.route != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = route != null ? route.hashCode() : 0;
        result = 31 * result + status_code;
        return result;
    }

    @Override
    public String getName() {
        return route_code;
    }

    public long getCount() {
        return count.get();
    }

    /**
     * increment the count
     */
    public void update() {
        count.incrementAndGet();
    }
}
