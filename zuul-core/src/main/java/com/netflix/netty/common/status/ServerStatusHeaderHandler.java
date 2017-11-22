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

package com.netflix.netty.common.status;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AsciiString;

import javax.inject.Singleton;

@Singleton
@ChannelHandler.Sharable
public class ServerStatusHeaderHandler extends ChannelOutboundHandlerAdapter
{
    public static final AsciiString INSTANCE_STATUS_HEADER_NAME = new AsciiString("x-netflix.instance-status");
    public static final AsciiString INSTANCE_HEALTH_HEADER_NAME = new AsciiString("x-netflix.instance-health");

    private final ServerStatusManager serverStatusManager;

    public ServerStatusHeaderHandler(ServerStatusManager serverStatusManager)
    {
        this.serverStatusManager = serverStatusManager;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

//            InstanceInfo.InstanceStatus status = serverStatusManager.status();
//            response.headers().set(INSTANCE_STATUS_HEADER_NAME, status.name());

            // TODO
            //response.headers().set(SERVER_HEALTH_HEADER_NAME, serverStatusManager.health());
        }

        super.write(ctx, msg, promise);
    }
}
