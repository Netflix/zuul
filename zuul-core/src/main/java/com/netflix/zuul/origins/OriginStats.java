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

package com.netflix.zuul.origins;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Information about an {@link Origin} to be used for proxying.
 */
@ThreadSafe
public final class OriginStats {

    /**
     * Represents the last time a Server in this Origin was throttled.
     */
    private final AtomicReference<ZonedDateTime> lastThrottleEvent = new AtomicReference<>();


    /**
     * Gets the last time a Server in this Origin was throttled.  Returns {@code null} if there was
     * no throttling.  This class does not define what throttling is; see
     * {@link com.netflix.zuul.filters.endpoint.ProxyEndpoint}.
     */
    @Nullable
    public ZonedDateTime lastThrottleEvent() {
        return lastThrottleEvent.get();
    }

    /**
     * Sets the last throttle event, if it is after the existing last throttle event.
     */
    public void lastThrottleEvent(ZonedDateTime lastThrottleEvent) {
        Objects.requireNonNull(lastThrottleEvent);
        ZonedDateTime existing;
        do {
            existing = this.lastThrottleEvent.get();
            if (existing != null && lastThrottleEvent.compareTo(existing) <= 0) {
                break;
            }
        } while (!this.lastThrottleEvent.compareAndSet(existing, lastThrottleEvent));
    }
}
