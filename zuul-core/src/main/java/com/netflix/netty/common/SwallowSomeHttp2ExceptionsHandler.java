/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spectator.api.Registry;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.unix.Errors;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class SwallowSomeHttp2ExceptionsHandler extends ChannelOutboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(SwallowSomeHttp2ExceptionsHandler.class);

    private final Registry registry;

    public SwallowSomeHttp2ExceptionsHandler(Registry registry)
    {
        this.registry = registry;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        incrementExceptionCounter(cause);

        if (cause instanceof Http2Exception) {
            Http2Exception h2e = (Http2Exception) cause;
            if (h2e.error() == Http2Error.NO_ERROR
                    && Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN.equals(h2e.shutdownHint())) {
                // This is the exception we threw ourselves to make the http2 codec gracefully close the connection. So just
                // swallow it so that it doesn't propagate and get logged.
                LOG.debug("Swallowed Http2Exception.ShutdownHint.GRACEFUL_SHUTDOWN ", cause);
            }
            else {
                super.exceptionCaught(ctx, cause);
            }
        }
        else if (cause instanceof Errors.NativeIoException) {
            LOG.debug("Swallowed NativeIoException", cause);
        }
        else {
            super.exceptionCaught(ctx, cause);
        }
    }

    private void incrementExceptionCounter(Throwable throwable)
    {
        registry.counter("server.connection.pipeline.exception",
                "id", throwable.getClass().getSimpleName())
                .increment();
    }
}
