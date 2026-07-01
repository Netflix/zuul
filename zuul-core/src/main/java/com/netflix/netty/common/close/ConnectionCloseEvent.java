/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.netty.common.close;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;

/**
 * Pipeline event requesting that a connection be closed.
 **/
@NullMarked
public sealed interface ConnectionCloseEvent {

    CloseReason reason();

    record Graceful(CloseReason reason) implements ConnectionCloseEvent {}

    /**
     * Close the connection after a random delay in {@code [0, maxJitter)}, useful for avoiding thundering herds when
     * closing a large number of connections
     */
    record GracefulDelayed(CloseReason reason, Duration maxJitter) implements ConnectionCloseEvent {
        public GracefulDelayed {
            if (maxJitter.toMillis() < 1) {
                throw new IllegalArgumentException("maxJitter must be at least 1 ms, but was " + maxJitter);
            }
        }
    }
}
