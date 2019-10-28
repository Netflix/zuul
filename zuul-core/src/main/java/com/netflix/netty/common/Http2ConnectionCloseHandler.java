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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.util.HttpUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DelegatingChannelPromiseNotifier;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 2:03 PM
 */
@ChannelHandler.Sharable
public class Http2ConnectionCloseHandler extends ChannelDuplexHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(Http2ConnectionCloseHandler.class);

    private final Registry registry;
    private final Id counterBaseId;

    @Inject
    public Http2ConnectionCloseHandler(Registry registry)
    {
        super();
        this.registry = registry;
        this.counterBaseId = registry.createId("server.connection.close.handled");
    }

    private void incrementCounter(ConnectionCloseType closeType, int port)
    {
        registry.counter(
                counterBaseId
                        .withTag("close_type", closeType.name())
                        .withTag("port", Integer.toString(port))
                        .withTag("protocol", "http2"))
                .increment();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        // Close the connection immediately after LastContent is written, rather than
        // waiting until the graceful-delay is up if this flag is set.
        if (isEndOfRequestResponse(msg)) {
            final Channel parent = HttpUtils.getMainChannel(ctx);
            final ChannelPromise closeAfterPromise = shouldCloseAfter(ctx, parent);
            if (closeAfterPromise != null) {

                // Add listener to close the channel AFTER response has been sent.
                promise.addListener(future -> {
                    // Close the parent (tcp connection) channel.
                    closeChannel(ctx, closeAfterPromise);
                });
            }
        }

        super.write(ctx, msg, promise);
    }

    /**
     * Look on both the stream channel, and the parent channel to see if the CLOSE_AFTER_RESPONSE flag has been set.
     * If so, return that promise.
     *
     * @param ctx
     * @param parent
     * @return
     */
    private ChannelPromise shouldCloseAfter(ChannelHandlerContext ctx, Channel parent)
    {
        ChannelPromise closeAfterPromise = ctx.channel().attr(ConnectionCloseChannelAttributes.CLOSE_AFTER_RESPONSE).get();
        if (closeAfterPromise == null) {
            closeAfterPromise = parent.attr(ConnectionCloseChannelAttributes.CLOSE_AFTER_RESPONSE).get();
        }
        return closeAfterPromise;
    }

    private boolean isEndOfRequestResponse(Object msg)
    {
        if (msg instanceof Http2HeadersFrame) {
            return ((Http2HeadersFrame) msg).isEndStream();
        }
        if (msg instanceof Http2DataFrame) {
            return ((Http2DataFrame) msg).isEndStream();
        }
        return false;
    }

    private void closeChannel(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
    {
        Channel child = ctx.channel();
        Channel parent = HttpUtils.getMainChannel(ctx);

        // 1. Check if already_closing flag on this stream channel. If there is, then success this promise and return.
        //    If not, then add already_closing flag to this stream channel.
        // 2. Check if already_closing flag on the parent channel.
        //    If so, then just return.
        //    If not, then set already_closing on parent channel, and then allow through.

        if (isAlreadyClosing(child)) {
            promise.setSuccess();
            return;
        }

        if (isAlreadyClosing(parent)) {
            return;
        }

        // Close according to the specified close type.
        ConnectionCloseType closeType = ConnectionCloseType.fromChannel(parent);
        Integer port = parent.attr(SourceAddressChannelHandler.ATTR_SERVER_LOCAL_PORT).get();
        port = port == null ? -1 :port;
        incrementCounter(closeType, port);
        switch (closeType) {
            case DELAYED_GRACEFUL:
                gracefullyWithDelay(ctx.executor(), parent, promise);
                break;
            case GRACEFUL:
            case IMMEDIATE:
                immediate(parent, promise);
                break;
            default:
                throw new IllegalArgumentException("Unknown ConnectionCloseEvent type! - " + closeType);
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
    private void gracefullyWithDelay(EventExecutor executor, Channel parent, ChannelPromise promise)
    {
        // See javadoc for explanation of why this may be disabled.
        boolean allowGracefulDelayed = ConnectionCloseChannelAttributes.allowGracefulDelayed(parent);
        if (! allowGracefulDelayed) {
            immediate(parent, promise);
            return;
        }

        if (! parent.isActive()) {
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
        parent.writeAndFlush(goaway);
        LOG.debug("gracefullyWithDelay: flushed initial go_away frame. channel=" + parent.id().asShortText());

        // In N secs time, throw an error that causes the http2 codec to send another GOAWAY frame
        // (this time with accurate lastStreamId) and then close the connection.
        int gracefulCloseDelay = ConnectionCloseChannelAttributes.gracefulCloseDelay(parent);
        executor.schedule(() -> {

            // Check that the client hasn't already closed the connection (due to the earlier goaway we sent).
            if (parent.isActive()) {
                // NOTE - the netty Http2ConnectionHandler specifically does not send another goaway when we call
                // channel.close() if one has already been sent .... so when we want more than one sent, we need to do it
                // explicitly ourselves like this.
                LOG.debug("gracefullyWithDelay: firing graceful_shutdown event to make netty send a final go_away frame and then close connection. channel="
                        + parent.id().asShortText());
                Http2Exception h2e = new Http2Exception(Http2Error.NO_ERROR, Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN);
                parent.pipeline().fireExceptionCaught(h2e);

                parent.close().addListener(future -> {
                    promise.setSuccess();
                });
            } else {
                promise.setSuccess();
            }

        }, gracefulCloseDelay, TimeUnit.SECONDS);
    }

    private void immediate(Channel parent, ChannelPromise promise)
    {
        if (parent.isActive()) {
            parent.close().addListener(new DelegatingChannelPromiseNotifier(promise));
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
}
