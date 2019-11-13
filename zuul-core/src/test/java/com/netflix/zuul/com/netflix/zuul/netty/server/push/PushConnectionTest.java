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

import com.netflix.zuul.netty.server.push.PushConnection;
import com.netflix.zuul.netty.server.push.PushProtocol;
import org.junit.Assert;
import org.junit.Test;

/**
 * Author: Susheel Aroskar
 * Date: 10/18/2018
 */
public class PushConnectionTest {

    @Test
    public void testOneMessagePerSecond() throws InterruptedException{
        final PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        for (int i=0; i < 5; i ++) {
            Assert.assertFalse(conn.isRateLimited());
            Thread.sleep(1000);
        }
    }

    @Test
    public void testThreeMessagesInSuccession() {
        final PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
    }

    @Test
    public void testFourMessagesInSuccession() {
        final PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertTrue(conn.isRateLimited());
    }

    @Test
    public void testFirstThreeMessagesSuccess() {
        final PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        for (int i=0; i < 10; i ++) {
            if (i < 3) {
                Assert.assertFalse(conn.isRateLimited());
            } else {
                Assert.assertTrue(conn.isRateLimited());
            }
        }
    }

    @Test
    public void testMessagesInBatches() throws InterruptedException{
        final PushConnection conn = new PushConnection(PushProtocol.WEBSOCKET, null);
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertTrue(conn.isRateLimited());
        Thread.sleep(2000);
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertFalse(conn.isRateLimited());
        Assert.assertTrue(conn.isRateLimited());
    }


}
