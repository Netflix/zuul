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

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.zuul.origins.OriginName;
import java.util.Objects;

/**
 * Created by saroskar on 3/24/16.
 */
public class ConnectionPoolConfigImpl implements ConnectionPoolConfig {

    static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
    static final int DEFAULT_CONNECT_TIMEOUT = 500;
    static final int DEFAULT_IDLE_TIMEOUT = 60000;
    static final int DEFAULT_MAX_CONNS_PER_HOST = 50;
    static final int DEFAULT_PER_SERVER_WATERLINE = 4;
    static final int DEFAULT_MAX_REQUESTS_PER_CONNECTION = 1000;

    // TODO(argha-c): Document why these values were chosen, as opposed to defaults of 32k/64k
    static final int DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK = 32 * 1024;
    static final int DEFAULT_WRITE_BUFFER_LOW_WATER_MARK = 8 * 1024;

    /**
     * NOTE that each eventloop has its own connection pool per host, and this is applied per event-loop.
     */
    public static final IClientConfigKey<Integer> PER_SERVER_WATERLINE =
            new CommonClientConfigKey<>("PerServerWaterline") {};

    public static final IClientConfigKey<Boolean> CLOSE_ON_CIRCUIT_BREAKER =
            new CommonClientConfigKey<>("CloseOnCircuitBreaker") {};

    public static final IClientConfigKey<Integer> MAX_REQUESTS_PER_CONNECTION =
            new CommonClientConfigKey<>("MaxRequestsPerConnection") {};

    public static final IClientConfigKey<Boolean> TCP_KEEP_ALIVE = new CommonClientConfigKey<>("TcpKeepAlive") {};

    public static final IClientConfigKey<Boolean> TCP_NO_DELAY = new CommonClientConfigKey<>("TcpNoDelay") {};

    public static final IClientConfigKey<Boolean> AUTO_READ = new CommonClientConfigKey<>("AutoRead") {};

    public static final IClientConfigKey<Integer> WRITE_BUFFER_HIGH_WATER_MARK =
            new CommonClientConfigKey<>("WriteBufferHighWaterMark") {};

    public static final IClientConfigKey<Integer> WRITE_BUFFER_LOW_WATER_MARK =
            new CommonClientConfigKey<>("WriteBufferLowWaterMark") {};

    private final OriginName originName;
    private final IClientConfig clientConfig;

    public ConnectionPoolConfigImpl(OriginName originName, IClientConfig clientConfig) {
        this.originName = Objects.requireNonNull(originName, "originName");
        this.clientConfig = clientConfig;
    }

    @Override
    public OriginName getOriginName() {
        return originName;
    }

    @Override
    public int getConnectTimeout() {
        return clientConfig.getPropertyAsInteger(IClientConfigKey.Keys.ConnectTimeout, DEFAULT_CONNECT_TIMEOUT);
    }

    @Override
    public int getMaxRequestsPerConnection() {
        return clientConfig.getPropertyAsInteger(MAX_REQUESTS_PER_CONNECTION, DEFAULT_MAX_REQUESTS_PER_CONNECTION);
    }

    @Override
    public int maxConnectionsPerHost() {
        return clientConfig.getPropertyAsInteger(
                IClientConfigKey.Keys.MaxConnectionsPerHost, DEFAULT_MAX_CONNS_PER_HOST);
    }

    @Override
    public int perServerWaterline() {
        return clientConfig.getPropertyAsInteger(PER_SERVER_WATERLINE, DEFAULT_PER_SERVER_WATERLINE);
    }

    @Override
    public int getIdleTimeout() {
        return clientConfig.getPropertyAsInteger(
                IClientConfigKey.Keys.ConnIdleEvictTimeMilliSeconds, DEFAULT_IDLE_TIMEOUT);
    }

    @Override
    public boolean getTcpKeepAlive() {
        return clientConfig.getPropertyAsBoolean(TCP_KEEP_ALIVE, false);
    }

    @Override
    public boolean getTcpNoDelay() {
        return clientConfig.getPropertyAsBoolean(TCP_NO_DELAY, false);
    }

    @Override
    public int getTcpReceiveBufferSize() {
        return clientConfig.getPropertyAsInteger(IClientConfigKey.Keys.ReceiveBufferSize, DEFAULT_BUFFER_SIZE);
    }

    @Override
    public int getTcpSendBufferSize() {
        return clientConfig.getPropertyAsInteger(IClientConfigKey.Keys.SendBufferSize, DEFAULT_BUFFER_SIZE);
    }

    @Override
    public int getNettyWriteBufferHighWaterMark() {
        return clientConfig.getPropertyAsInteger(WRITE_BUFFER_HIGH_WATER_MARK, DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK);
    }

    @Override
    public int getNettyWriteBufferLowWaterMark() {
        return clientConfig.getPropertyAsInteger(WRITE_BUFFER_LOW_WATER_MARK, DEFAULT_WRITE_BUFFER_LOW_WATER_MARK);
    }

    @Override
    public boolean getNettyAutoRead() {
        return clientConfig.getPropertyAsBoolean(AUTO_READ, false);
    }

    @Override
    public boolean isSecure() {
        return clientConfig.getPropertyAsBoolean(IClientConfigKey.Keys.IsSecure, false);
    }

    @Override
    public boolean useIPAddrForServer() {
        return clientConfig.getPropertyAsBoolean(IClientConfigKey.Keys.UseIPAddrForServer, true);
    }

    @Override
    public boolean isCloseOnCircuitBreakerEnabled() {
        return clientConfig.getPropertyAsBoolean(CLOSE_ON_CIRCUIT_BREAKER, true);
    }
}
