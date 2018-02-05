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

package com.netflix.zuul.netty.insights;

import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * User: Mike Smith
 * Date: 9/24/16
 * Time: 2:41 PM
 */
public class PassportStateHttpClientHandler extends CombinedChannelDuplexHandler
{
    public static final String PASSPORT_STATE_HTTP_CLIENT_HANDLER_NAME = "PassportStateHttpClientHandler";

    public PassportStateHttpClientHandler()
    {
        super(new InboundHandler(), new OutboundHandler());
    }

    private static CurrentPassport passport(ChannelHandlerContext ctx)
    {
        return CurrentPassport.fromChannel(ctx.channel());
    }

    private static class InboundHandler extends ChannelInboundHandlerAdapter
    {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            try {
                CurrentPassport passport = passport(ctx);

                if (msg instanceof HttpResponse) {
                    passport.add(PassportState.IN_RESP_HEADERS_RECEIVED);
                }

                if (msg instanceof LastHttpContent) {
                    passport.add(PassportState.IN_RESP_LAST_CONTENT_RECEIVED);
                }
                else if (msg instanceof HttpContent) {
                    passport.add(PassportState.IN_RESP_CONTENT_RECEIVED);
                }
            }
            finally {
                super.channelRead(ctx, msg);
            }
        }
    }

    private static class OutboundHandler extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            try {
                CurrentPassport passport = passport(ctx);

                if (msg instanceof HttpRequest) {
                    passport.add(PassportState.OUT_REQ_HEADERS_SENDING);
                    promise.addListener(new PassportStateListener(passport, 
                            PassportState.OUT_REQ_HEADERS_SENT,
                            PassportState.OUT_REQ_HEADERS_ERROR_SENDING));
                }
                
                if (msg instanceof LastHttpContent) {
                    passport.add(PassportState.OUT_REQ_LAST_CONTENT_SENDING);
                    promise.addListener(new PassportStateListener(passport, 
                            PassportState.OUT_REQ_LAST_CONTENT_SENT,
                            PassportState.OUT_REQ_LAST_CONTENT_ERROR_SENDING));
                }
                else if (msg instanceof HttpContent) {
                    passport.add(PassportState.OUT_REQ_CONTENT_SENDING);
                    promise.addListener(new PassportStateListener(passport, 
                            PassportState.OUT_REQ_CONTENT_SENT,
                            PassportState.OUT_REQ_CONTENT_ERROR_SENDING));
                }
            }
            finally {
                super.write(ctx, msg, promise);
            }
        }
    }
}
