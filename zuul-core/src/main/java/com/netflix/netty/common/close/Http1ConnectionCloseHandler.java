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

import com.netflix.netty.common.HttpLifecycleChannelHandler.CompleteEvent;
import com.netflix.netty.common.HttpLifecycleChannelHandler.StartEvent;
import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.jspecify.annotations.NullMarked;

/**
 * Gracefully closes http/1.1 connections in response to a {@link ConnectionCloseEvent}. If no request is in flight the
 * connection is closed immediately; otherwise a {@code Connection: close} header is set on the in-flight response and
 * the connection is closed once that response has been fully written.
 */
@NullMarked
public class Http1ConnectionCloseHandler extends BaseConnectionCloseHandler {

    private boolean requestInFlight;

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
                int port = getPort(ctx.channel());
                scheduleCloseTimeout(
                        () -> {
                            countHandled(port, "timeout");
                            cf.channel().close();
                        },
                        cf.channel());
            });
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof StartEvent) {
            requestInFlight = true;
        } else if (evt instanceof CompleteEvent) {
            requestInFlight = false;
        }

        super.userEventTriggered(ctx, evt);
    }
    /**
     * Closes the channel immediately when no request is in flight; otherwise the in-flight response is left to trigger
     * the close via {@link #write(ChannelHandlerContext, Object, io.netty.channel.ChannelPromise)}.
     */
    @Override
    protected void onCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event) {
        if (!requestInFlight) {
            countHandled(getPort(ctx.channel()), "idle");
            ctx.close();
        }
    }

    @Override
    protected String getProtocol() {
        return "http/1.1";
    }
}
