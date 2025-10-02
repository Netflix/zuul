/*
 * Copyright 2024 Netflix, Inc.
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

import static com.netflix.zuul.netty.connectionpool.ConnectionPoolConfigImpl.MAX_REQUESTS_PER_CONNECTION;
import static com.netflix.zuul.netty.connectionpool.ConnectionPoolConfigImpl.PER_SERVER_WATERLINE;
import static com.netflix.zuul.netty.connectionpool.ConnectionPoolConfigImpl.TCP_KEEP_ALIVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.zuul.origins.OriginName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Justin Guerra
 * @since 6/20/24
 */
class ConnectionPoolConfigImplTest {

    private ConnectionPoolConfig connectionPoolConfig;
    private DefaultClientConfigImpl clientConfig;

    @BeforeEach
    void setup() {
        OriginName originName = OriginName.fromVip("whatever");
        clientConfig = DefaultClientConfigImpl.getEmptyConfig();
        connectionPoolConfig = new ConnectionPoolConfigImpl(originName, clientConfig);
    }

    @Test
    void testGetConnectTimeout() {
        assertThat(connectionPoolConfig.getConnectTimeout())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_CONNECT_TIMEOUT);
    }

    @Test
    void testGetConnectTimeoutOverride() {
        clientConfig.set(IClientConfigKey.Keys.ConnectTimeout, 1000);
        assertThat(connectionPoolConfig.getConnectTimeout()).isEqualTo(1000);
    }

    @Test
    void testGetMaxRequestsPerConnection() {
        assertThat(connectionPoolConfig.getMaxRequestsPerConnection())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_MAX_REQUESTS_PER_CONNECTION);
    }

    @Test
    void testGetMaxRequestsPerConnectionOverride() {
        clientConfig.set(MAX_REQUESTS_PER_CONNECTION, 2000);
        assertThat(connectionPoolConfig.getMaxRequestsPerConnection()).isEqualTo(2000);
    }

    @Test
    void testMaxConnectionsPerHost() {
        assertThat(connectionPoolConfig.maxConnectionsPerHost())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_MAX_CONNS_PER_HOST);
    }

    @Test
    void testMaxConnectionsPerHostOverride() {
        clientConfig.set(IClientConfigKey.Keys.MaxConnectionsPerHost, 60);
        assertThat(connectionPoolConfig.maxConnectionsPerHost()).isEqualTo(60);
    }

    @Test
    void testPerServerWaterline() {
        assertThat(connectionPoolConfig.perServerWaterline())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_PER_SERVER_WATERLINE);
    }

    @Test
    void testPerServerWaterlineOverride() {
        clientConfig.set(PER_SERVER_WATERLINE, 5);
        assertThat(connectionPoolConfig.perServerWaterline()).isEqualTo(5);
    }

    @Test
    void testGetIdleTimeout() {
        assertThat(connectionPoolConfig.getIdleTimeout()).isEqualTo(ConnectionPoolConfigImpl.DEFAULT_IDLE_TIMEOUT);
    }

    @Test
    void testGetIdleTimeoutOverride() {
        clientConfig.set(IClientConfigKey.Keys.ConnIdleEvictTimeMilliSeconds, 70000);
        assertThat(connectionPoolConfig.getIdleTimeout()).isEqualTo(70000);
    }

    @Test
    void testGetTcpKeepAlive() {
        assertThat(connectionPoolConfig.getTcpKeepAlive()).isFalse();
    }

    @Test
    void testGetTcpKeepAliveOverride() {
        clientConfig.set(TCP_KEEP_ALIVE, true);
        assertThat(connectionPoolConfig.getTcpKeepAlive()).isTrue();
    }

    @Test
    void testGetTcpNoDelay() {
        assertThat(connectionPoolConfig.getTcpNoDelay()).isFalse();
    }

    @Test
    void testGetTcpNoDelayOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.TCP_NO_DELAY, true);
        assertThat(connectionPoolConfig.getTcpNoDelay()).isTrue();
    }

    @Test
    void testGetTcpReceiveBufferSize() {
        assertThat(connectionPoolConfig.getTcpReceiveBufferSize())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_BUFFER_SIZE);
    }

    @Test
    void testGetTcpReceiveBufferSizeOverride() {
        clientConfig.set(IClientConfigKey.Keys.ReceiveBufferSize, 40000);
        assertThat(connectionPoolConfig.getTcpReceiveBufferSize()).isEqualTo(40000);
    }

    @Test
    void testGetTcpSendBufferSize() {
        assertThat(connectionPoolConfig.getTcpSendBufferSize()).isEqualTo(ConnectionPoolConfigImpl.DEFAULT_BUFFER_SIZE);
    }

    @Test
    void testGetTcpSendBufferSizeOverride() {
        clientConfig.set(IClientConfigKey.Keys.SendBufferSize, 40000);
        assertThat(connectionPoolConfig.getTcpSendBufferSize()).isEqualTo(40000);
    }

    @Test
    void testGetNettyWriteBufferHighWaterMark() {
        assertThat(connectionPoolConfig.getNettyWriteBufferHighWaterMark())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK);
    }

    @Test
    void testGetNettyWriteBufferHighWaterMarkOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.WRITE_BUFFER_HIGH_WATER_MARK, 40000);
        assertThat(connectionPoolConfig.getNettyWriteBufferHighWaterMark()).isEqualTo(40000);
    }

    @Test
    void testGetNettyWriteBufferLowWaterMark() {
        assertThat(connectionPoolConfig.getNettyWriteBufferLowWaterMark())
                .isEqualTo(ConnectionPoolConfigImpl.DEFAULT_WRITE_BUFFER_LOW_WATER_MARK);
    }

    @Test
    void testGetNettyWriteBufferLowWaterMarkOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.WRITE_BUFFER_LOW_WATER_MARK, 10000);
        assertThat(connectionPoolConfig.getNettyWriteBufferLowWaterMark()).isEqualTo(10000);
    }

    @Test
    void testGetNettyAutoRead() {
        assertThat(connectionPoolConfig.getNettyAutoRead()).isFalse();
    }

    @Test
    void testGetNettyAutoReadOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.AUTO_READ, true);
        assertThat(connectionPoolConfig.getNettyAutoRead()).isTrue();
    }

    @Test
    void testIsSecure() {
        assertThat(connectionPoolConfig.isSecure()).isFalse();
    }

    @Test
    void testIsSecureOverride() {
        clientConfig.set(IClientConfigKey.Keys.IsSecure, true);
        assertThat(connectionPoolConfig.isSecure()).isTrue();
    }

    @Test
    void testUseIPAddrForServer() {
        assertThat(connectionPoolConfig.useIPAddrForServer()).isTrue();
    }

    @Test
    void testUseIPAddrForServerOverride() {
        clientConfig.set(IClientConfigKey.Keys.UseIPAddrForServer, false);
        assertThat(connectionPoolConfig.useIPAddrForServer()).isFalse();
    }

    @Test
    void testIsCloseOnCircuitBreakerEnabled() {
        assertThat(connectionPoolConfig.isCloseOnCircuitBreakerEnabled()).isTrue();
    }

    @Test
    void testIsCloseOnCircuitBreakerEnabledOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.CLOSE_ON_CIRCUIT_BREAKER, false);
        assertThat(connectionPoolConfig.isCloseOnCircuitBreakerEnabled()).isFalse();
    }
}
