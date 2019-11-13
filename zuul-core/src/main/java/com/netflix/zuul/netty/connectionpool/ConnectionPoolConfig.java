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

/**
 * Created by saroskar on 3/24/16.
 */
public interface ConnectionPoolConfig {

    /* Origin name from connection pool */
    String getOriginName();

    /* Max number of requests per connection before it needs to be recycled */
    int getMaxRequestsPerConnection();

    /* Max connections per host */
    int maxConnectionsPerHost();

    int perServerWaterline();

    /* Origin client TCP configuration options */
    int getConnectTimeout();

    /* number of milliseconds connection can stay idle in a connection pool before it is closed */
    int getIdleTimeout();

    int getTcpReceiveBufferSize();

    int getTcpSendBufferSize();

    int getNettyWriteBufferHighWaterMark();

    int getNettyWriteBufferLowWaterMark();

    boolean getTcpKeepAlive();

    boolean getTcpNoDelay();

    boolean getNettyAutoRead();

    boolean isSecure();

    boolean useIPAddrForServer();
}
