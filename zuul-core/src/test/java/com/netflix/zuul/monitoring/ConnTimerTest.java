/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnTimerTest {
    @Test
    public void record() {
        EmbeddedChannel chan = new EmbeddedChannel();
        Attrs attrs = Attrs.newInstance();
        chan.attr(Server.CONN_DIMENSIONS).set(attrs);
        Registry registry = new DefaultRegistry();
        ConnTimer timer = ConnTimer.install(chan, registry, registry.createId("foo"));

        timer.record(1000L, "start");
        timer.record(2000L, "middle");
        Attrs.newKey("bar").put(attrs, "baz");
        timer.record(4000L, "end");

        PercentileTimer meter1 =
                PercentileTimer.get(registry, registry.createId("foo.start-middle"));
        assertNotNull(meter1);
        assertEquals(1000L, meter1.totalTime());

        PercentileTimer meter2 =
                PercentileTimer.get(registry, registry.createId("foo.middle-end", "bar", "baz"));
        assertNotNull(meter2);
        assertEquals(2000L, meter2.totalTime());

        PercentileTimer meter3 =
                PercentileTimer.get(registry, registry.createId("foo.start-end", "bar", "baz"));
        assertNotNull(meter3);
        assertEquals(3000L, meter3.totalTime());
    }
}
