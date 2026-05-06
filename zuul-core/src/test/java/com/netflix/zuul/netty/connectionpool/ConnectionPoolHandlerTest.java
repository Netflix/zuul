/*
 * Copyright 2026 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.netflix.netty.common.HttpClientLifecycleChannelHandler;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteReason;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.origins.OriginName;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionPoolHandlerTest {

    @Mock
    private ClientChannelManager channelManager;

    private DefaultRegistry registry;
    private ConnectionPoolMetrics metrics;
    private ConnectionPoolHandler handler;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        registry = new DefaultRegistry();
        OriginName originName = OriginName.fromVipAndApp("whatever", "whatever");
        metrics = ConnectionPoolMetrics.create(originName, registry);
        handler = new ConnectionPoolHandler(metrics);
        channel = new EmbeddedChannel(handler);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    void sessionCompleteReleasesConnectionToPoolWhenNothingPending() {
        PooledConnection conn = newPooledConnection();

        channel.pipeline().fireUserEventTriggered(new CompleteEvent(CompleteReason.SESSION_COMPLETE, null, null));

        verify(channelManager).release(conn);
        assertThat(metrics.outboundIncompleteCounter().count()).isZero();
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void sessionCompleteClosesConnectionWhenLastContentStillPending() {
        PooledConnection conn = newPooledConnection();
        channel.attr(HttpClientLifecycleChannelHandler.ATTR_OUTBOUND_LAST_CONTENT_PENDING)
                .set(Boolean.TRUE);

        channel.pipeline().fireUserEventTriggered(new CompleteEvent(CompleteReason.SESSION_COMPLETE, null, null));

        assertThat(metrics.outboundIncompleteCounter().count()).isEqualTo(1);
        assertThat(conn.isShouldClose()).isTrue();
        verify(channelManager).release(conn);
    }

    private PooledConnection newPooledConnection() {
        return new PooledConnection(
                channel,
                DiscoveryResult.EMPTY,
                channelManager,
                registry.counter("close"),
                registry.counter("closeBusy"));
    }
}
