/**
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.sample.push;

import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.zuul.netty.server.ZuulDependencyKeys;
import com.netflix.zuul.netty.server.push.*;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public class SampleWebSocketPushChannelInitializer extends PushChannelInitializer {

    private final PushConnectionRegistry pushConnectionRegistry;
    private final PushAuthHandler pushAuthHandler;

    public SampleWebSocketPushChannelInitializer(int port, ChannelConfig channelConfig, ChannelConfig channelDependencies, ChannelGroup channels) {
        super(port, channelConfig, channelDependencies, channels);
        pushConnectionRegistry = channelDependencies.get(ZuulDependencyKeys.pushConnectionRegistry);
        pushAuthHandler = new SamplePushAuthHandler(PushProtocol.WEBSOCKET.getPath());
    }


    @Override
    protected void addPushHandlers(final ChannelPipeline pipeline) {
        pipeline.addLast(PushAuthHandler.NAME, pushAuthHandler);
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler(PushProtocol.WEBSOCKET.getPath(), null, true));
        pipeline.addLast(new SampleWebSocketPushRegistrationHandler(pushConnectionRegistry));
    }


}
