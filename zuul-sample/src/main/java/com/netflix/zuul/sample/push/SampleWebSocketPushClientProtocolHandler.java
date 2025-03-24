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

import com.netflix.zuul.netty.server.push.PushClientProtocolHandler;
import com.netflix.zuul.netty.server.push.PushProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public class SampleWebSocketPushClientProtocolHandler extends PushClientProtocolHandler {

    private static final Logger logger = LoggerFactory.getLogger(SampleWebSocketPushClientProtocolHandler.class);

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (!isAuthenticated()) {
                // Do not entertain ANY message from unauthenticated client
                PushProtocol.WEBSOCKET.sendErrorAndClose(ctx, 1007, "Missing authentication");
            } else if (msg instanceof PingWebSocketFrame) {
                logger.debug("received ping frame");
                ctx.writeAndFlush(new PongWebSocketFrame());
            } else if (msg instanceof CloseWebSocketFrame) {
                logger.debug("received close frame");
                ctx.close();
            } else if (msg instanceof TextWebSocketFrame tf) {
                
                String text = tf.text();
                logger.debug("received test frame: {}", text);
                if (text != null && text.startsWith("ECHO ")) { // echo protocol
                    ctx.channel().writeAndFlush(tf.copy());
                }
            } else if (msg instanceof BinaryWebSocketFrame) {
                logger.debug("received binary frame");
                PushProtocol.WEBSOCKET.sendErrorAndClose(ctx, 1003, "Binary WebSocket frames not supported");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
