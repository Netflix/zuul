/**
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.netty.common;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Just listens for the IdleStateEvent and closes the channel if received.
 */
public class CloseOnIdleStateHandler extends ChannelInboundHandlerAdapter {

    private final Counter counter;

    public CloseOnIdleStateHandler(Registry registry, String metricId) {
        this.counter = registry.counter("server.connections.idle.timeout", "id", metricId);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);

        if (evt instanceof IdleStateEvent) {
            counter.increment();
            ctx.close();
        }
    }
}
