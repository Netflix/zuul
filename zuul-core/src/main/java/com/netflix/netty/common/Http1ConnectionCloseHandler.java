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

package com.netflix.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 2:03 PM
 */
public class Http1ConnectionCloseHandler extends ChannelDuplexHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(Http1ConnectionCloseHandler.class);

    private final int gracefulCloseDelay;
    private final AtomicBoolean requestInflight = new AtomicBoolean(Boolean.FALSE);

    public Http1ConnectionCloseHandler(int gracefulCloseDelay)
    {
        this.gracefulCloseDelay = gracefulCloseDelay;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

            if (HttpChannelFlags.CLOSE_AFTER_RESPONSE.get(ctx)) {
                // Add header to tell client that they should close this connection.
                response.headers().set(HttpHeaderNames.CONNECTION, "close");
            }
        }

        super.write(ctx, msg, promise);

        // Close the connection immediately after LastContent is written, rather than
        // waiting until the graceful-delay is up if this flag is set.
        if (msg instanceof LastHttpContent) {
            if (HttpChannelFlags.CLOSE_AFTER_RESPONSE.get(ctx)) {
                promise.addListener(future -> {
                    ctx.close();
                });
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        // Track when there's an inflight request.
        if (evt instanceof HttpLifecycleChannelHandler.StartEvent) {
            requestInflight.set(Boolean.TRUE);
        }
        else if (evt instanceof HttpLifecycleChannelHandler.CompleteEvent) {
            requestInflight.set(Boolean.FALSE);
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
    {
        // Close according to the specified close type.
        ConnectionCloseType type = ConnectionCloseType.fromChannel(ctx.channel());
        closeChannel(ctx, type, promise);

        // Don't pass this event further down the pipeline.
    }

    protected void closeChannel(ChannelHandlerContext ctx, ConnectionCloseType evt, ChannelPromise promise)
    {
        switch (evt) {
            case DELAYED_GRACEFUL:
                gracefully(ctx, promise);
                break;
            case GRACEFUL:
                gracefully(ctx, promise);
                break;
            case IMMEDIATE:
                immediately(ctx, promise);
                break;
            default:
                throw new IllegalArgumentException("Unknown ConnectionCloseEvent type! - " + String.valueOf(evt));
        }
    }

    protected void gracefully(ChannelHandlerContext ctx, ChannelPromise promise)
    {
        if (isAlreadyClosing(ctx)) {
            promise.setSuccess();
            return;
        }

        final Channel channel = ctx.channel();
        if (channel.isActive()) {
            final String channelId = channel.id().asShortText();

            // Flag this channel to be closed after any inflight request completes.
            HttpChannelFlags.CLOSE_AFTER_RESPONSE.set(ctx);

            // In gracefulCloseDelay secs time, go ahead and close the connection if it hasn't already been.
            ctx.executor().schedule(() -> {

                // Check that the client hasn't already closed the connection.
                if (channel.isActive()) {

                    // If there is still an inflight request, then don't close the conn now. Instead assume that it will be closed
                    // either after the response finally gets written (due to us having set the CLOSE_AFTER_RESPONSE flag), or when the IdleTimeout
                    // for this conn fires.
                    if (requestInflight.get()) {
                        LOG.debug("gracefully: firing graceful_shutdown event to close connection, but request still inflight, so leaving. channel=" + channelId);
                    }
                    else {
                        LOG.debug("gracefully: firing graceful_shutdown event to close connection. channel=" + channelId);
                        ctx.close(promise);
                    }

                }
                else {
                    LOG.debug("gracefully: connection already closed. channel=" + channelId);
                    promise.setSuccess();
                }

            }, gracefulCloseDelay, TimeUnit.SECONDS);

        }
        else {
            promise.setSuccess();
        }
    }

    protected void immediately(ChannelHandlerContext ctx, ChannelPromise promise)
    {
        if (isAlreadyClosing(ctx)) {
            promise.setSuccess();
            return;
        }

        if (ctx.channel().isActive()) {
            ctx.close(promise);
        }
        else {
            promise.setSuccess();
        }
    }

    protected boolean isAlreadyClosing(ChannelHandlerContext ctx)
    {
        // If already closing, then just return.
        if (HttpChannelFlags.CLOSING.get(ctx)) {
            return true;
        }
        else {
            HttpChannelFlags.CLOSING.set(ctx);
            HttpChannelFlags.CLOSE_AFTER_RESPONSE.set(ctx);
            return false;
        }
    }
}
