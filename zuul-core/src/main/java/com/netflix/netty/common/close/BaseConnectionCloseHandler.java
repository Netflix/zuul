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

package com.netflix.netty.common.close;

import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;

/**
 * @author Justin Guerra
 * @since 6/25/26
 */
abstract class BaseConnectionCloseHandler extends ChannelDuplexHandler {

    protected final Registry registry;

    protected ConnectionCloseEvent closeEvent;

    BaseConnectionCloseHandler(Registry registry) {
        this.registry = registry;
    }

    protected abstract String getProtocol();

    protected int getPort(Channel channel) {
        Integer port =
                channel.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).get();
        return port != null ? port : -1;
    }

    protected boolean isFlaggedForClose() {
        return closeEvent != null;
    }

    protected void count(ConnectionCloseEvent event, int port) {
        Id tags = registry.createId("server.connection.close.handled")
                .withTag("close_type", closeType(event))
                .withTag("close_reason", event.reason().name())
                .withTag("port", Integer.toString(port))
                .withTag("protocol", getProtocol());
        registry.counter(tags).increment();
    }

    private static String closeType(ConnectionCloseEvent event) {
        return switch (event) {
            case ConnectionCloseEvent.Graceful ignored -> "GRACEFUL";
            case ConnectionCloseEvent.GracefulDelayed ignored -> "GRACEFUL_DELAYED";
        };
    }
}
