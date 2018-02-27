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

import com.netflix.config.DynamicBooleanProperty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 2:03 PM
 */
@ChannelHandler.Sharable
public class Http2ConnectionCloseHandler extends ChannelOutboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(Http2ConnectionCloseHandler.class);

    private static final DynamicBooleanProperty ALLOW_GRACEFUL_DELAYED = new DynamicBooleanProperty(
            "server.connection.close.graceful.delayed.allow", true);

    private static final SwallowHttp2ExceptionShutdownHint SWALLOW_EXCEPTION_HANDLER = new SwallowHttp2ExceptionShutdownHint();

    private final int gracefulCloseDelay;

    public Http2ConnectionCloseHandler(int gracefulCloseDelay)
    {
        this.gracefulCloseDelay = gracefulCloseDelay;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception
    {
        // Add a handler that just catches the Http2Exception that we fire to tell the codec to gracefully shutdown a connection.
        // We want to catch it so that it doesn't get logged by the DefaultChannelPipeline as if it was a _real_
        // exception.
        ChannelPipeline parentPipeline = ctx.channel().parent().pipeline();
        String handlerName = "h2_exception_swallow_handler";
        if (parentPipeline.get(handlerName) == null) {
            parentPipeline.addLast(handlerName, SWALLOW_EXCEPTION_HANDLER);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        super.write(ctx, msg, promise);

        // Close the connection immediately after LastContent is written, rather than
        // waiting until the graceful-delay is up if this flag is set.
        if (isEndOfRequestResponse(msg)) {
            if (HttpChannelFlags.CLOSE_AFTER_RESPONSE.get(ctx)) {
                promise.addListener(future -> {
                    Channel parent = parentChannel(ctx);
                    closeChannel(ctx, parent, ConnectionCloseType.fromChannel(ctx.channel()), ctx.newPromise());
                });
            }
        }
    }

    protected boolean isEndOfRequestResponse(Object msg)
    {
        if (msg instanceof Http2HeadersFrame) {
            return ((Http2HeadersFrame) msg).isEndStream();
        }
        if (msg instanceof Http2DataFrame) {
            return ((Http2DataFrame) msg).isEndStream();
        }
        return false;
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
    {
        // Close according to the specified close type.
        Channel parent = parentChannel(ctx);
        ConnectionCloseType type = ConnectionCloseType.fromChannel(parent);
        closeChannel(ctx, parent, type, promise);

        // Don't pass this event further down the pipeline.
    }

    protected void closeChannel(ChannelHandlerContext ctx, Channel channel, ConnectionCloseType evt, ChannelPromise promise)
    {
        switch (evt) {
            case DELAYED_GRACEFUL:
                gracefullyWithDelay(ctx, channel, promise);
                break;
            case GRACEFUL:
                gracefully(channel, promise);
                break;
            case IMMEDIATE:
                immediately(channel, promise);
                break;
            default:
                throw new IllegalArgumentException("Unknown ConnectionCloseEvent type! - " + String.valueOf(evt));
        }
    }

    /**
     * WARNING: Found the OkHttp client gets confused by this behaviour (it ends up putting itself in a bad shutdown state
     * after receiving the first goaway frame, and then dropping any inflight responses but also timing out waiting for them).
     *
     * And worried that other http/2 stacks may be similar, so for now we should NOT use this.
     *
     * This is unfortunate, as FTL wanted this, and it is correct according to the spec.
     *
     * See this code in okhttp where it drops response header frame if state is already shutdown:
     * https://github.com/square/okhttp/blob/master/okhttp/src/main/java/okhttp3/internal/http2/Http2Connection.java#L609
     */
    protected void gracefullyWithDelay(ChannelHandlerContext ctx, Channel channel, ChannelPromise promise)
    {
        // See javadoc for explanation of why this may be disabled.
        if (! ALLOW_GRACEFUL_DELAYED.get()) {
            gracefully(channel, promise);
            return;
        }

        if (isAlreadyClosing(channel)) {
            promise.setSuccess();
            return;
        }

        // First send a 'graceful shutdown' GOAWAY frame.
        /*
        "A server that is attempting to gracefully shut down a connection SHOULD send an initial GOAWAY frame with
        the last stream identifier set to 231-1 and a NO_ERROR code. This signals to the client that a shutdown is
        imminent and that initiating further requests is prohibited."
          -- https://http2.github.io/http2-spec/#GOAWAY
         */
        DefaultHttp2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR);
        goaway.setExtraStreamIds(Integer.MAX_VALUE);
        channel.writeAndFlush(goaway);
        LOG.debug("gracefullyWithDelay: flushed initial go_away frame. channel=" + channel.id().asShortText());

        // In N secs time, throw an error that causes the http2 codec to send another GOAWAY frame
        // (this time with accurate lastStreamId) and then close the connection.
        ctx.executor().schedule(() -> {

            // Check that the client hasn't already closed the connection (due to the earlier goaway we sent).
            gracefulConnectionShutdown(channel);
            promise.setSuccess();

        }, gracefulCloseDelay, TimeUnit.SECONDS);
    }

    protected void gracefulConnectionShutdown(Channel channel)
    {
        if (channel.isActive()) {
            LOG.debug("gracefullyWithDelay: firing graceful_shutdown event to make netty send a final go_away frame and then close connection. channel="
                    + channel.id().asShortText());
            Http2Exception h2e = new Http2Exception(Http2Error.NO_ERROR, Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN);
            channel.pipeline().fireExceptionCaught(h2e);
        }
        else {
            LOG.debug("gracefullyWithDelay: connection already closed, so no need to send final go_away frame. channel=" + channel.id().asShortText());
        }
    }

    protected void gracefully(Channel channel, ChannelPromise promise)
    {
        if (isAlreadyClosing(channel)) {
            promise.setSuccess();
            return;
        }

        gracefulConnectionShutdown(channel);
        promise.setSuccess();
    }

    protected void immediately(Channel parent, ChannelPromise promise)
    {
        if (isAlreadyClosing(parent)) {
            promise.setSuccess();
            return;
        }

        if (parent.isActive()) {
            parent.close(promise);
        }
        else {
            promise.setSuccess();
        }
    }

    protected boolean isAlreadyClosing(Channel parentChannel)
    {
        // If already closing, then just return.
        // This will happen because close() is called a 2nd time after sending the goaway frame.
        if (HttpChannelFlags.CLOSING.get(parentChannel)) {
            return true;
        }
        else {
            HttpChannelFlags.CLOSING.set(parentChannel);
            return false;
        }
    }

    protected Channel parentChannel(ChannelHandlerContext ctx) {
        return ctx.channel().parent();
    }


    @Sharable
    static class SwallowHttp2ExceptionShutdownHint extends ChannelOutboundHandlerAdapter
    {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
        {
            if (cause instanceof Http2Exception) {
                Http2Exception h2e = (Http2Exception) cause;
                if (h2e.error() == Http2Error.NO_ERROR
                        && Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN.equals(h2e.shutdownHint())) {
                    // This is the exception we threw ourselves to make the http2 codec gracefully close the connection. So just
                    // swallow it so that it doesn't propagate and get logged.
                }
                else {
                    super.exceptionCaught(ctx, cause);
                }
            }
            else {
                super.exceptionCaught(ctx, cause);
            }

        }
    }
}
