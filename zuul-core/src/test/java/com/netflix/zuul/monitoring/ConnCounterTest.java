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
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Attrs;
import com.netflix.zuul.netty.server.Server;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnCounterTest {
    @Test
    public void record() {
        EmbeddedChannel chan = new EmbeddedChannel();
        Attrs attrs = Attrs.newInstance();
        chan.attr(Server.CONN_DIMENSIONS).set(attrs);
        Registry registry = new DefaultRegistry();
        ConnCounter counter = ConnCounter.install(chan, registry, registry.createId("foo"));

        counter.increment("start");
        counter.increment("middle");
        Attrs.newKey("bar").put(attrs, "baz");
        counter.increment("end");

        Gauge meter1 = registry.gauge(registry.createId("foo.start", "from", "nascent"));
        assertNotNull(meter1);
        assertEquals(1, meter1.value(), 0);

        Gauge meter2 = registry.gauge(registry.createId("foo.middle", "from", "start"));
        assertNotNull(meter2);
        assertEquals(1, meter2.value(), 0);

        Gauge meter3 = registry.gauge(registry.createId("foo.end", "from", "middle", "bar", "baz"));
        assertNotNull(meter3);
        assertEquals(1, meter3.value(), 0);
    }

    @Test
    public void activeConnsCount() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Attrs attrs = Attrs.newInstance();
        channel.attr(Server.CONN_DIMENSIONS).set(attrs);
        Registry registry = new DefaultRegistry();

        ConnCounter.install(channel, registry, registry.createId("foo"));

        // Dedup increments
        ConnCounter.from(channel).increment("active");
        ConnCounter.from(channel).increment("active");


        assertEquals(1, ConnCounter.from(channel).getCurrentActiveConns(), 0);
    }
}
