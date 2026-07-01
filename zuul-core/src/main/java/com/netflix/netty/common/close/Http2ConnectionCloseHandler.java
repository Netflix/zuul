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

import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

/**
 * Http/2 connection level handler that is responsible for handling {@link ConnectionCloseEvent} and closing connections.
 */
@Slf4j
@NullMarked
public class Http2ConnectionCloseHandler extends BaseConnectionCloseHandler {

    public Http2ConnectionCloseHandler(Registry registry) {
        super(registry);
    }

    @Override
    protected String getProtocol() {
        return "http/2";
    }

    @Override
    protected void handleCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event) {
        if (isFlaggedForClose()) {
            return;
        }
        int port = getPort(ctx.channel());

        flagForClose(event);
        countFlagged(port);

        /*
        First send a 'graceful shutdown' GOAWAY frame.

        "A server that is attempting to gracefully shut down a connection SHOULD send an initial GOAWAY frame with
        the last stream identifier set to 231-1 and a NO_ERROR code. This signals to the client that a shutdown is
        imminent and that initiating further requests is prohibited."
          -- https://http2.github.io/http2-spec/#GOAWAY

         */
        DefaultHttp2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR);
        goaway.setExtraStreamIds(Integer.MAX_VALUE);
        ctx.writeAndFlush(goaway);

        scheduleCloseTimeout(
                () -> {
                    // In N secs time, throw an error that causes the Http2ConnectionHandler to send another GOAWAY
                    // frame (this time with accurate lastStreamId) and schedule a close after the window
                    Http2Exception h2e =
                            new Http2Exception(Http2Error.NO_ERROR, Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN);
                    ctx.pipeline().fireExceptionCaught(h2e);
                },
                ctx.channel());
    }
}
