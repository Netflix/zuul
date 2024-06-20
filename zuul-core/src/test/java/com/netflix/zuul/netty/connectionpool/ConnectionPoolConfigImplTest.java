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

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.zuul.origins.OriginName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.netflix.zuul.netty.connectionpool.ConnectionPoolConfigImpl.MAX_REQUESTS_PER_CONNECTION;
import static com.netflix.zuul.netty.connectionpool.ConnectionPoolConfigImpl.PER_SERVER_WATERLINE;
import static com.netflix.zuul.netty.connectionpool.ConnectionPoolConfigImpl.TCP_KEEP_ALIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(ConnectionPoolConfigImpl.DEFAULT_CONNECT_TIMEOUT, connectionPoolConfig.getConnectTimeout());
    }

    @Test
    void testGetConnectTimeoutOverride() {
        clientConfig.set(IClientConfigKey.Keys.ConnectTimeout, 1000);
        assertEquals(1000, connectionPoolConfig.getConnectTimeout());
    }

    @Test
    void testGetMaxRequestsPerConnection() {
        assertEquals(
                ConnectionPoolConfigImpl.DEFAULT_MAX_REQUESTS_PER_CONNECTION,
                connectionPoolConfig.getMaxRequestsPerConnection());
    }

    @Test
    void testGetMaxRequestsPerConnectionOverride() {
        clientConfig.set(MAX_REQUESTS_PER_CONNECTION, 2000);
        assertEquals(2000, connectionPoolConfig.getMaxRequestsPerConnection());
    }

    @Test
    void testMaxConnectionsPerHost() {
        assertEquals(ConnectionPoolConfigImpl.DEFAULT_MAX_CONNS_PER_HOST, connectionPoolConfig.maxConnectionsPerHost());
    }

    @Test
    void testMaxConnectionsPerHostOverride() {
        clientConfig.set(IClientConfigKey.Keys.MaxConnectionsPerHost, 60);
        assertEquals(60, connectionPoolConfig.maxConnectionsPerHost());
    }

    @Test
    void testPerServerWaterline() {
        assertEquals(ConnectionPoolConfigImpl.DEFAULT_PER_SERVER_WATERLINE, connectionPoolConfig.perServerWaterline());
    }

    @Test
    void testPerServerWaterlineOverride() {
        clientConfig.set(PER_SERVER_WATERLINE, 5);
        assertEquals(5, connectionPoolConfig.perServerWaterline());
    }

    @Test
    void testGetIdleTimeout() {
        assertEquals(ConnectionPoolConfigImpl.DEFAULT_IDLE_TIMEOUT, connectionPoolConfig.getIdleTimeout());
    }

    @Test
    void testGetIdleTimeoutOverride() {
        clientConfig.set(IClientConfigKey.Keys.ConnIdleEvictTimeMilliSeconds, 70000);
        assertEquals(70000, connectionPoolConfig.getIdleTimeout());
    }

    @Test
    void testGetTcpKeepAlive() {
        assertFalse(connectionPoolConfig.getTcpKeepAlive());
    }

    @Test
    void testGetTcpKeepAliveOverride() {
        clientConfig.set(TCP_KEEP_ALIVE, true);
        assertTrue(connectionPoolConfig.getTcpKeepAlive());
    }

    @Test
    void testGetTcpNoDelay() {
        assertFalse(connectionPoolConfig.getTcpNoDelay());
    }

    @Test
    void testGetTcpNoDelayOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.TCP_NO_DELAY, true);
        assertTrue(connectionPoolConfig.getTcpNoDelay());
    }

    @Test
    void testGetTcpReceiveBufferSize() {
        assertEquals(ConnectionPoolConfigImpl.DEFAULT_BUFFER_SIZE, connectionPoolConfig.getTcpReceiveBufferSize());
    }

    @Test
    void testGetTcpReceiveBufferSizeOverride() {
        clientConfig.set(IClientConfigKey.Keys.ReceiveBufferSize, 40000);
        assertEquals(40000, connectionPoolConfig.getTcpReceiveBufferSize());
    }

    @Test
    void testGetTcpSendBufferSize() {
        assertEquals(ConnectionPoolConfigImpl.DEFAULT_BUFFER_SIZE, connectionPoolConfig.getTcpSendBufferSize());
    }

    @Test
    void testGetTcpSendBufferSizeOverride() {
        clientConfig.set(IClientConfigKey.Keys.SendBufferSize, 40000);
        assertEquals(40000, connectionPoolConfig.getTcpSendBufferSize());
    }

    @Test
    void testGetNettyWriteBufferHighWaterMark() {
        assertEquals(
                ConnectionPoolConfigImpl.DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK,
                connectionPoolConfig.getNettyWriteBufferHighWaterMark());
    }

    @Test
    void testGetNettyWriteBufferHighWaterMarkOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.WRITE_BUFFER_HIGH_WATER_MARK, 40000);
        assertEquals(40000, connectionPoolConfig.getNettyWriteBufferHighWaterMark());
    }

    @Test
    void testGetNettyWriteBufferLowWaterMark() {
        assertEquals(
                ConnectionPoolConfigImpl.DEFAULT_WRITE_BUFFER_LOW_WATER_MARK,
                connectionPoolConfig.getNettyWriteBufferLowWaterMark());
    }

    @Test
    void testGetNettyWriteBufferLowWaterMarkOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.WRITE_BUFFER_LOW_WATER_MARK, 10000);
        assertEquals(10000, connectionPoolConfig.getNettyWriteBufferLowWaterMark());
    }

    @Test
    void testGetNettyAutoRead() {
        assertFalse(connectionPoolConfig.getNettyAutoRead());
    }

    @Test
    void testGetNettyAutoReadOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.AUTO_READ, true);
        assertTrue(connectionPoolConfig.getNettyAutoRead());
    }

    @Test
    void testIsSecure() {
        assertFalse(connectionPoolConfig.isSecure());
    }

    @Test
    void testIsSecureOverride() {
        clientConfig.set(IClientConfigKey.Keys.IsSecure, true);
        assertTrue(connectionPoolConfig.isSecure());
    }

    @Test
    void testUseIPAddrForServer() {
        assertTrue(connectionPoolConfig.useIPAddrForServer());
    }

    @Test
    void testUseIPAddrForServerOverride() {
        clientConfig.set(IClientConfigKey.Keys.UseIPAddrForServer, false);
        assertFalse(connectionPoolConfig.useIPAddrForServer());
    }

    @Test
    void testIsCloseOnCircuitBreakerEnabled() {
        assertTrue(connectionPoolConfig.isCloseOnCircuitBreakerEnabled());
    }

    @Test
    void testIsCloseOnCircuitBreakerEnabledOverride() {
        clientConfig.set(ConnectionPoolConfigImpl.CLOSE_ON_CIRCUIT_BREAKER, false);
        assertFalse(connectionPoolConfig.isCloseOnCircuitBreakerEnabled());
    }
}
