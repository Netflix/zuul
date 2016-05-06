/*
 * Copyright 2013 Netflix, Inc.
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


    String routeCode;

    String route;
    int statusCode;

    @Monitor(name = "count", type = DataSourceType.COUNTER)
    private final AtomicLong count = new AtomicLong();


    public RouteStatusCodeMonitor(String route, int status_code) {
        if(route == null) route = "";
        this.route = route;
        this.statusCode = status_code;
        routeCode = route + "_" + status_code;
        tagList = BasicTagList.of(new BasicTag("ID", routeCode));

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RouteStatusCodeMonitor statsData = (RouteStatusCodeMonitor) o;

        if (statusCode != statsData.statusCode) return false;
        if (route != null ? !route.equals(statsData.route) : statsData.route != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = route != null ? route.hashCode() : 0;
        result = 31 * result + statusCode;
        return result;
    }

    @Override
    public String getName() {
        return routeCode;
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

    public int getStatusCode() {
        return statusCode;
    }

    public String getRoute() {
        return route;
    }

    public String getRouteCode() {
        return routeCode;
    }
}
