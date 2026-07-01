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
 * Gracefully closes an http/2 connection in response to a {@link ConnectionCloseEvent}. Must sit on the
 * connection-level (parent) pipeline.
 *
 * <p>Closing happens in two phases. First an initial "graceful shutdown" GOAWAY frame is sent with a last stream id of
 * {@code 2^31 - 1} and a {@code NO_ERROR} code. Per the HTTP/2 spec this tells the client a shutdown is imminent and
 * that it should not initiate further requests, while still allowing in-flight streams to complete:
 *
 * <blockquote>"A server that is attempting to gracefully shut down a connection SHOULD send an initial GOAWAY frame
 * with the last stream identifier set to 2^31-1 and a NO_ERROR code."
 * -- <a href="https://http2.github.io/http2-spec/#GOAWAY">HTTP/2 spec</a></blockquote>
 *
 * <p>Then, once the close timeout elapses, an {@link Http2Exception} carrying
 * {@link Http2Exception.ShutdownHint#GRACEFUL_SHUTDOWN} is fired so netty's {@code Http2ConnectionHandler} sends a
 * second GOAWAY - this time with an accurate last stream id - and closes the connection after its shutdown window.
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
    protected void onCloseEvent(ChannelHandlerContext ctx, ConnectionCloseEvent event) {
        DefaultHttp2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR);
        goaway.setExtraStreamIds(Integer.MAX_VALUE);
        ctx.writeAndFlush(goaway);

        scheduleCloseTimeout(
                () -> {
                    countHandled(getPort(ctx.channel()), "timeout");
                    Http2Exception h2e =
                            new Http2Exception(Http2Error.NO_ERROR, Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN);
                    ctx.pipeline().fireExceptionCaught(h2e);
                },
                ctx.channel());
    }
}
