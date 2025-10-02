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

package com.netflix.zuul.com.netflix.zuul.netty.server.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.zuul.netty.server.push.PushConnection;
import com.netflix.zuul.netty.server.push.PushProtocol;
import org.junit.jupiter.api.Test;

/**
 * Author: Susheel Aroskar
 * Date: 10/18/2018
 */
class PushConnectionTest {

    @Test
    void testOneMessagePerSecond() throws InterruptedException {
        PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        for (int i = 0; i < 5; i++) {
            assertThat(conn.isRateLimited()).isFalse();
            Thread.sleep(1000);
        }
    }

    @Test
    void testThreeMessagesInSuccession() {
        PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
    }

    @Test
    void testFourMessagesInSuccession() {
        PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isTrue();
    }

    @Test
    void testFirstThreeMessagesSuccess() {
        PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        for (int i = 0; i < 10; i++) {
            if (i < 3) {
                assertThat(conn.isRateLimited()).isFalse();
            } else {
                assertThat(conn.isRateLimited()).isTrue();
            }
        }
    }

    @Test
    void testMessagesInBatches() throws InterruptedException {
        PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isTrue();
        Thread.sleep(2000);
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isFalse();
        assertThat(conn.isRateLimited()).isTrue();
    }
}
