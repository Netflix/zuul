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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectionCloseEventTest {

    @Test
    void gracefulDelayedRejectsZeroJitter() {
        assertThatThrownBy(() -> new ConnectionCloseEvent.GracefulDelayed(CloseReason.SHUTDOWN, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gracefulDelayedRejectsVerySmallJitter() {
        assertThatThrownBy(() -> new ConnectionCloseEvent.GracefulDelayed(CloseReason.SHUTDOWN, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gracefulDelayedRejectsNegativeJitter() {
        assertThatThrownBy(() -> new ConnectionCloseEvent.GracefulDelayed(CloseReason.SHUTDOWN, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
