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

package com.netflix.zuul.netty.server.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;

/**
 * The Http2ServerDowngrader currently is always incorrectly setting the "x-http2-stream-id"
 * header to "0", which is confusing. And as we don't actually need it and the other "x-http2-" headers, we
 * strip them out here to avoid the confusion.
 *
 * Hopefully in a future netty release that header value will be correct and we can then
 * stop doing this. Although potentially we _never_ want to pass these downstream to origins .... ?
 */
@ChannelHandler.Sharable
public class Http2StreamHeaderCleaner extends ChannelInboundHandlerAdapter
{
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            for (String name : req.headers().names()) {
                if (name.startsWith("x-http2-")) {
                    req.headers().remove(name);
                }
            }
        }

        super.channelRead(ctx, msg);
    }
}

