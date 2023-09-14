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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.zuul.stats.monitoring.NamedCount;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of a Named counter to monitor and count error causes by route. Route is a defined zuul concept to
 * categorize requests into buckets. By default this is the first segment of the uri
 * @author Mikey Cohen
 * Date: 2/23/12
 * Time: 4:16 PM
 */
public class ErrorStatsData implements NamedCount {
    private final String id;

    private final String errorCause;
    private final AtomicLong count = new AtomicLong();

    /**
     * create a counter by route and cause of error
     * @param route
     * @param cause
     */
    public ErrorStatsData(String route, String cause) {
        if(null == route || "".equals(route)){
            route = "UNKNOWN";
        }
        id = route + "_" + cause;

        this.errorCause = cause;
        Registry registry = Spectator.globalRegistry();
        PolledMeter.using(registry)
                .withId(registry.createId("zuul.ErrorStatsData", "ID", id))
                .monitorValue(this, ErrorStatsData::getCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ErrorStatsData that = (ErrorStatsData) o;

        return !(errorCause != null ? !errorCause.equals(that.errorCause) : that.errorCause != null);

    }

    @Override
    public int hashCode() {
        return errorCause != null ? errorCause.hashCode() : 0;
    }

    /**
     * increments the counter
     */
    public void update() {
        count.incrementAndGet();
    }

    @Override
    public String getName() {
        return id;
    }

    @Override
    public long getCount() {
        return count.get();
    }
}
