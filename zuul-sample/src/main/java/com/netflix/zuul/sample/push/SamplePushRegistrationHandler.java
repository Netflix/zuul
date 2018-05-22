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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.server.push.PushProtocol;
import com.netflix.zuul.netty.server.push.PushRegistrationHandler;
import com.netflix.zuul.netty.server.push.PushUserAuth;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public class SamplePushRegistrationHandler extends PushRegistrationHandler {

    public SamplePushRegistrationHandler(PushConnectionRegistry pushConnectionRegistry, PushProtocol pushProtocol) {
        super(pushConnectionRegistry, pushProtocol);
    }

    @Override
    protected void handleTextWebSocketFrame(PushUserAuth authEvent, PushConnectionRegistry.PushConnection conn, String text) {
        if (text != null && text.startsWith("ECHO ")) { //echo protocol
            conn.sendPushMessage(text);
        }
        else if ("NOOP".equals(text)) {
            // Do nothing
        }
    }

    @Override
    protected void handleBinaryWebSocketFrame(PushUserAuth authEvent, PushConnectionRegistry.PushConnection conn, ByteBuf byteBuf) {
        sendErrorAndClose(1003, "Binary WebSocket frames not supported");
    }

    @Override
    protected WebSocketFrame goAwayFrame() {
        return new TextWebSocketFrame("_CLOSE_");
    }

}
