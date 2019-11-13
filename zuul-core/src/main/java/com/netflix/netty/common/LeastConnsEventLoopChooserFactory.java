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

package com.netflix.netty.common;

import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * User: michaels@netflix.com
 * Date: 2/7/17
 * Time: 2:44 PM
 */
public class LeastConnsEventLoopChooserFactory implements EventExecutorChooserFactory
{
    private static final Logger LOG = LoggerFactory.getLogger(LeastConnsEventLoopChooserFactory.class);
    private final EventLoopGroupMetrics groupMetrics;

    public LeastConnsEventLoopChooserFactory(EventLoopGroupMetrics groupMetrics)
    {
        this.groupMetrics = groupMetrics;
    }

    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors)
    {
        return new LeastConnsEventExecutorChooser(executors, groupMetrics);
    }

    private static class LeastConnsEventExecutorChooser implements EventExecutorChooser
    {
        private final List<EventExecutor> executors;
        private final EventLoopGroupMetrics groupMetrics;

        public LeastConnsEventExecutorChooser(EventExecutor[] executors, final EventLoopGroupMetrics groupMetrics)
        {
            this.executors = Arrays.asList(executors);
            this.groupMetrics = groupMetrics;
        }

        @Override
        public EventExecutor next()
        {
            return chooseWithLeastConns();
        }

        private EventExecutor chooseWithLeastConns()
        {
            EventExecutor leastExec = null;
            int leastValue = Integer.MAX_VALUE;
            
            Map<Thread, Integer> connsPer = groupMetrics.connectionsPerEventLoop();

            // Shuffle the list of executors each time so that if they all have the same number of connections, then
            // we don't favour the 1st one.
            Collections.shuffle(executors, ThreadLocalRandom.current());
            
            for (EventExecutor executor : executors)
            {
                int value = connsPer.getOrDefault(executor, 0);
                if (value < leastValue) {
                    leastValue = value;
                    leastExec = executor;
                }
            }

            // If none chosen, then just use first.
            if (leastExec == null) {
                leastExec = executors.get(0);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Chose eventloop: " + String.valueOf(leastExec)
                        + ", leastValue=" + leastValue
                        + ", connsPer=" + String.valueOf(connsPer));
            }

            return leastExec;
        }
    }
}
