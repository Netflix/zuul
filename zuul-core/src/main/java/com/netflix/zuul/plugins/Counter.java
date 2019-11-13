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
package com.netflix.zuul.plugins;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.InjectableTag;
import com.netflix.servo.tag.Tag;
import com.netflix.zuul.monitoring.CounterFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Plugin to hook up a Servo counter to the CounterFactory
 * @author Mikey Cohen
 * Date: 4/10/13
 * Time: 4:50 PM
 */
public class Counter extends CounterFactory {
    final static ConcurrentMap<String, BasicCounter> map = new ConcurrentHashMap<String, BasicCounter>();
    final Object lock = new Object();

    @Override
    public void increment(String name) {
        BasicCounter counter = getCounter(name);
        counter.increment();
    }

    private BasicCounter getCounter(String name) {
        BasicCounter counter = map.get(name);
        if (counter == null) {
            synchronized (lock) {
                counter = map.get(name);
                if (counter != null) {
                    return counter;
                }

                List<Tag> tags = new ArrayList<Tag>(2);
                tags.add(InjectableTag.HOSTNAME);
                tags.add(InjectableTag.IP);
                counter = new BasicCounter(MonitorConfig.builder(name).withTags(tags).build());
                map.putIfAbsent(name, counter);
                DefaultMonitorRegistry.getInstance().register(counter);
            }
        }
        return counter;
    }
}
