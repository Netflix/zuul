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

package com.netflix.netty.common.close;

import com.netflix.netty.common.HttpLifecycleChannelHandler;
import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 2:03 PM
 */
public class Http1ConnectionCloseHandler extends BaseConnectionCloseHandler {

    private boolean requestInFlight;
    private ScheduledFuture<?> closeFuture;

    public Http1ConnectionCloseHandler(Registry registry) {
        super(registry);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse response && isFlaggedForClose()) {
            // Add header to tell client that they should close this connection.
            response.headers().set(HttpHeaderNames.CONNECTION, "close");
        }

        if (msg instanceof LastHttpContent && isFlaggedForClose()) {
            ctx.write(msg, promise).addListener((ChannelFuture cf) -> {
                count(closeEvent, getPort(ctx.channel()));
                cf.channel().close();
            });
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpLifecycleChannelHandler.StartEvent) {
            requestInFlight = true;
        } else if (evt instanceof HttpLifecycleChannelHandler.CompleteEvent) {
            requestInFlight = false;
        } else if (evt instanceof ConnectionCloseEvent event) {
            handleCloseEvent(ctx, event);
        }

        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (closeFuture != null) {
            closeFuture.cancel(false);
        }

        ctx.fireChannelInactive();
    }

    private void handleCloseEvent(ChannelHandlerContext context, ConnectionCloseEvent event) {
        if (isFlaggedForClose()) {
            return;
        }

        closeEvent = event;
        if (requestInFlight) {
            return;
        }

        int port = getPort(context.channel());
        switch (event) {
            case ConnectionCloseEvent.Graceful ignored -> {
                count(event, port);
                context.close();
            }
            case ConnectionCloseEvent.GracefulDelayed delayed -> {
                long jitter =
                        ThreadLocalRandom.current().nextLong(delayed.maxJitter().toMillis());
                closeFuture = context.executor()
                        .schedule(
                                () -> {
                                    if (!requestInFlight) {
                                        count(event, port);
                                        context.close();
                                    }
                                },
                                jitter,
                                TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    protected String getProtocol() {
        return "http/1.1";
    }
}
