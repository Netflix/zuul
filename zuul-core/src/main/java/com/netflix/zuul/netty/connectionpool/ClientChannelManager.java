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

package com.netflix.zuul.netty.connectionpool;


import com.netflix.loadbalancer.Server;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.atomic.AtomicReference;

/**
 * User: michaels@netflix.com
 * Date: 7/8/16
 * Time: 12:36 PM
 */
public interface ClientChannelManager
{
    void init();

    boolean isAvailable();

    int getInflightRequestsCount();

    void shutdown();

    boolean release(PooledConnection conn);

    Promise<PooledConnection> acquire(EventLoop eventLoop);

    Promise<PooledConnection> acquire(EventLoop eventLoop, Object key, String httpMethod, String uri, int retryNum,
                                      CurrentPassport passport, AtomicReference<Server> selectedServer);

    boolean isCold();

    boolean remove(PooledConnection conn);

    int getConnsInPool();

    int getConnsInUse();

    ConnectionPoolConfig getConfig();
}
