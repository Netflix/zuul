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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Author: Susheel Aroskar
 * Date: 11/2/2018
 */
public class PushClientProtocolHandler extends ChannelInboundHandlerAdapter {

    protected PushUserAuth authEvent;


    protected boolean isAuthenticated() {
        return (authEvent != null && authEvent.isSuccess());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof PushUserAuth) {
            authEvent = (PushUserAuth) evt;
        }
        super.userEventTriggered(ctx, evt);
    }

}
