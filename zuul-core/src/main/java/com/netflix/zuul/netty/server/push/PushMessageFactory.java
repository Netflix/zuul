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

package com.netflix.zuul.netty.server.push;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * Author: Susheel Aroskar
 * Date: 11/2/2018
 */
public abstract class PushMessageFactory {



    public final void sendErrorAndClose(ChannelHandlerContext ctx, int statusCode, String reasonText) {
        ctx.writeAndFlush(serverClosingConnectionMessage(statusCode, reasonText))
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Application level protocol for asking client to close connection
     * @return WebSocketFrame which when sent to client will cause it to close the WebSocket
     */
    protected abstract Object goAwayMessage();

    /**
     * Message server sends to the client just before it force closes connection from its side
     * @return
     */
    protected abstract Object serverClosingConnectionMessage(int statusCode, String reasonText);

}
